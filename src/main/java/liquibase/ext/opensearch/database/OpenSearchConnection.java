package liquibase.ext.opensearch.database;

import liquibase.exception.DatabaseException;
import liquibase.nosql.database.AbstractNoSqlConnection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
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
import org.opensearch.client.opensearch._types.OpenSearchVersionInfo;
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
    private Optional<OpenSearchVersionInfo> openSearchVersion = Optional.empty();

    private List<URI> uris;
    private Properties connectionProperties;

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

        this.connectionProperties = driverProperties;

        try {
            this.uris = Arrays.stream(realUrl.split(OPENSEARCH_URI_SEPARATOR))
                    .map(this::toUri)
                    .filter(Objects::nonNull)
                    .toList();
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
    public void close() throws DatabaseException {
        this.openSearchClient = null;
        this.connectionProperties = null;
        this.uris = null;
    }

    @Override
    public String getCatalog() throws DatabaseException {
        return null; // OpenSearch doesn't have catalogs (called schemas in various RDBMS)
    }

    @Override
    public String getDatabaseProductName() throws DatabaseException {
        return OpenSearchLiquibaseDatabase.PRODUCT_NAME;
    }

    @Override
    public String getURL() {
        return this.uris.stream()
                .map(URI::toString)
                .collect(Collectors.joining(OPENSEARCH_URI_SEPARATOR));
    }

    @Override
    public String getConnectionUserName() {
        return this.connectionProperties.getProperty("username");
    }

    @Override
    public boolean isClosed() throws DatabaseException {
        return this.openSearchClient == null;
    }

    private void connect() {
        final var hosts = this.uris.stream().map(HttpHost::create).toList();
        final var hostsArray = hosts.toArray(HttpHost[]::new);

        final var transport = ApacheHttpClient5TransportBuilder
                .builder(hostsArray)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // TODO: support other credential providers
                    final var username = Optional.ofNullable(this.connectionProperties.getProperty("user"));
                    final var password = Optional.ofNullable(this.connectionProperties.getProperty("password"));

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
        return this.getOpenSearchVersion().number();
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

    private OpenSearchVersionInfo getOpenSearchVersion() throws DatabaseException {
        if (this.openSearchVersion.isEmpty()) {
            try {
                this.openSearchVersion = Optional.of(this.openSearchClient.info().version());
            } catch (IOException e) {
                throw new DatabaseException(e);
            }
        }
        return this.openSearchVersion.get();
    }

}
