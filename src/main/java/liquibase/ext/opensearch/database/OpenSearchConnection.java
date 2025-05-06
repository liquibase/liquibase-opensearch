package liquibase.ext.opensearch.database;

import liquibase.exception.DatabaseException;
import liquibase.nosql.database.AbstractNoSqlConnection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.InfoResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.sql.Driver;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase.OPENSEARCH_PREFIX;
import static liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase.OPENSEARCH_URI_SEPARATOR;

@Getter
@Setter
@NoArgsConstructor
public class OpenSearchConnection extends AbstractNoSqlConnection {

    private OpenSearchClient openSearchClient;
    private Optional<InfoResponse> openSearchInfo = Optional.empty();

    ///  URIs used to connect to OpenSearch. not present if an existing `OpenSearchClient` is passed instead
    private Optional<List<URI>> uris = Optional.empty();
    ///  connection properties from liquibase used to connect to OpenSearch. not present if an existing `OpenSearchClient` is passed instead
    private Optional<Properties> connectionProperties = Optional.empty();

    /**
     * Construct a new liquibase connection with an existing OpenSearchClient. Use this when you wish to re-use
     * an existing connection and/or use special client configuration, e.g. authentication other than basic auth.
     *
     * @param openSearchClient a fully configured client connected to an OpenSearch cluster.
     */
    public OpenSearchConnection(final OpenSearchClient openSearchClient) {
        super();
        this.openSearchClient = openSearchClient;
    }

    @Override
    public boolean supports(final String url) {
        if (url == null) {
            return false;
        }
        return url.toLowerCase().startsWith(OPENSEARCH_PREFIX);
    }

    @Override
    public void open(final String url, final Driver driverObject, final Properties driverProperties) throws DatabaseException {
        String realUrl = url;
        if (realUrl.toLowerCase().startsWith(OPENSEARCH_PREFIX)) {
            realUrl = realUrl.substring(OPENSEARCH_PREFIX.length());
        }

        this.connectionProperties = Optional.of(driverProperties);

        try {
            this.uris = Optional.of(Arrays.stream(realUrl.split(OPENSEARCH_URI_SEPARATOR))
                    .map(this::toUri)
                    .filter(Objects::nonNull)
                    .toList());
            this.connect();
        } catch (final Exception e) {
            throw new DatabaseException("Could not open connection to database: " + realUrl, e);
        }
    }

    private URI toUri(String uri) {
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public void close() {
        this.openSearchClient = null;
        this.connectionProperties = Optional.empty();
        this.uris = Optional.empty();
    }

    @Override
    public String getCatalog() {
        return null; // OpenSearch doesn't have catalogs (called schemas in various RDBMS)
    }

    @Override
    public String getDatabaseProductName() {
        return OpenSearchLiquibaseDatabase.PRODUCT_NAME;
    }

    @Override
    public String getURL() {
        // if we have a connection we should return the name of the cluster
        // this makes more sense than a list of URIs (which all point to the same cluster anyway).
        if (this.openSearchClient != null) {
            try {
                return this.getOpenSearchInfo().clusterName();
            } catch (final Exception e) {
                // do nothing, continue with alternative
            }
        }

        return this.uris.stream()
                .flatMap(List::stream)
                .map(URI::toString)
                .collect(Collectors.joining(OPENSEARCH_URI_SEPARATOR));
    }

    @Override
    public String getConnectionUserName() {
        return this.connectionProperties.map(p -> p.getProperty("username")).orElse("");
    }

    @Override
    public boolean isClosed() {
        return this.openSearchClient == null;
    }

    private void connect() {
        // safety: this is being called from `open` which ensures that `this.uris` is set to a non-empty value.
        //         calling it from elsewhere is wrong and will result in an exception if `this.uris` isn't set.
        final var hosts = this.uris.get().stream().map(HttpHost::create).toList();
        final var hostsArray = hosts.toArray(HttpHost[]::new);

        final var transport = ApacheHttpClient5TransportBuilder
                .builder(hostsArray)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // TODO: support other credential providers
                    final var username = this.connectionProperties.flatMap(p -> Optional.ofNullable(p.getProperty("user")));
                    final var password = this.connectionProperties.flatMap(p -> Optional.ofNullable(p.getProperty("password")));

                    if (username.isPresent()) {
                        final var credentialsProvider = new BasicCredentialsProvider();
                        final var credentials = new UsernamePasswordCredentials(username.get(), password.orElse("").toCharArray());
                        hosts.forEach(host -> credentialsProvider.setCredentials(new AuthScope(host), credentials));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    } else if (password.isPresent()) {
                        throw new RuntimeException("password provided but username not set!");
                    }

                    final SSLContext sslcontext;
                    try {
                        sslcontext = SSLContextBuilder
                                .create()
                                .loadTrustMaterial(null, (chains, authType) -> true)
                                .build();
                    } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                        throw new RuntimeException(e);
                    }

                    final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                            .setSslContext(sslcontext)
                            // disable the certificate since our testing cluster just uses the default security configuration
                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                            // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
                            .setTlsDetailsFactory(sslEngine -> new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol()))
                            .build();

                    final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                            .setTlsStrategy(tlsStrategy)
                            .build();

                    return httpClientBuilder
                            .setConnectionManager(connectionManager);
                })
                .setMapper(new JacksonJsonpMapper())
                .build();

        this.openSearchClient = new OpenSearchClient(transport);
    }

    @Override
    public String getDatabaseProductVersion() throws DatabaseException {
        return this.getOpenSearchInfo().version().number();
    }

    @Override
    public int getDatabaseMajorVersion() throws DatabaseException {
        final var version = this.getDatabaseProductVersion();
        return Integer.parseInt(version.split("\\.")[0]);
    }

    @Override
    public int getDatabaseMinorVersion() throws DatabaseException {
        final var version = this.getDatabaseProductVersion();
        return Integer.parseInt(version.split("\\.")[1]);
    }

    private InfoResponse getOpenSearchInfo() throws DatabaseException {
        if (this.openSearchInfo.isEmpty()) {
            try {
                this.openSearchInfo = Optional.of(this.openSearchClient.info());
            } catch (IOException e) {
                throw new DatabaseException(e);
            }
        }
        return this.openSearchInfo.get();
    }

}
