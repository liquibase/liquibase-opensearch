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
import org.opensearch.client.opensearch._types.OpenSearchVersionInfo;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.sql.Driver;
import java.util.Optional;
import java.util.Properties;

import static liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase.OPENSEARCH_PREFIX;

@Getter
@Setter
@NoArgsConstructor
public class OpenSearchConnection extends AbstractNoSqlConnection {

    private OpenSearchClient openSearchClient;
    private Optional<OpenSearchVersionInfo> openSearchVersion = Optional.empty();

    private URI uri;
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
            this.uri = new URI(realUrl);
            this.connect(this.uri, driverProperties);
        } catch (final Exception e) {
            throw new DatabaseException("Could not open connection to database: " + realUrl);
        }
    }

    @Override
    public void close() throws DatabaseException {
        this.openSearchClient = null;
        this.connectionProperties = null;
        this.uri = null;
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
        return this.uri.toString();
    }

    @Override
    public String getConnectionUserName() {
        return this.connectionProperties.getProperty("username");
    }

    @Override
    public boolean isClosed() throws DatabaseException {
        return this.openSearchClient == null;
    }

    private void connect(final URI uri, final Properties info) throws DatabaseException {
        final HttpHost host = HttpHost.create(uri);

        final var transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // TODO: support other credential providers
                    final var username = Optional.ofNullable(info.getProperty("user"));
                    final var password = Optional.ofNullable(info.getProperty("password"));

                    if (username.isPresent()) {
                        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(new AuthScope(host),
                                new UsernamePasswordCredentials(username.get(), password.orElse("").toCharArray()));

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
