package liquibase.ext.opensearch;

import liquibase.database.ConnectionServiceFactory;
import liquibase.database.DatabaseFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomOpenSearchClientLiquibaseIT extends AbstractOpenSearchLiquibaseIT {

    @SneakyThrows
    private OpenSearchClient newOpenSearchClientFromContainer() {
        final var host = HttpHost.create(this.container.getHttpHostAddress());

        final var transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    final var username = Optional.ofNullable(this.container.getUsername());
                    final var password = Optional.ofNullable(this.container.getPassword());

                    if (username.isPresent()) {
                        final var credentialsProvider = new BasicCredentialsProvider();
                        final var credentials = new UsernamePasswordCredentials(username.get(), password.orElse("").toCharArray());
                        credentialsProvider.setCredentials(new AuthScope(host), credentials);
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    } else if (password.isPresent()) {
                        throw new RuntimeException("password provided but username not set!");
                    }

                    final SSLContext sslcontext;
                    try {
                        sslcontext = SSLContextBuilder
                                .create()
                                .loadTrustMaterial(null, new TrustAllStrategy())
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

        return new OpenSearchClient(transport);
    }

    @SneakyThrows
    @BeforeEach
    @Override
    protected void beforeEach() {
        // we want to use our own connection (e.g. because we have requirements which are not fulfilled by the standard
        // client constructed by liquibase).
        // => do not rely on liquibase' standard mechanism of constructing a new connection, instead we force it to take our own.
        this.connection = new OpenSearchConnection(this.newOpenSearchClientFromContainer());
        ConnectionServiceFactory.getInstance().register(this.connection);
        this.database = new OpenSearchLiquibaseDatabase(this.connection);
        DatabaseFactory.getInstance().register(this.database);
    }

    @SneakyThrows
    @Test
    void itCreatesTheChangelogAndLockIndices() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        assertThat(this.indexExists(this.database.getDatabaseChangeLogLockTableName())).isTrue();
        assertThat(this.indexExists(this.database.getDatabaseChangeLogTableName())).isTrue();
    }

}
