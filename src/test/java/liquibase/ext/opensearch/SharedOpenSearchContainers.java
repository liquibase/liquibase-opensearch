package liquibase.ext.opensearch;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.json.jackson3.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * Starting an OpenSearch container takes far longer than any of the integration tests, thus all tests which need the
 * same kind of container (same image, with or without security) share one (singleton container pattern). If you
 * configure the container differently in any other way, extend the key in {@link #get(OpenSearchContainer)}. To keep
 * the memory usage low only one shared container is kept alive at any time (test classes run one after another). The
 * last one is removed by Testcontainers (Ryuk) when the JVM exits.
 * <p>
 * As the cluster is shared, {@link #reset(OpenSearchContainer)} has to be called before every test to give it a clean
 * cluster.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SharedOpenSearchContainers {

    private record SharedContainer(OpenSearchContainer<?> container, OpenSearchClient adminClient) {
    }

    private static final Map<String, SharedContainer> CONTAINERS = new HashMap<>();

    /**
     * @param candidate a configured but not yet started container. it is only started (and then shared) if no
     *                  container with the same image and configuration is running yet.
     * @return a running container with the same image and configuration as {@code candidate}.
     */
    static synchronized OpenSearchContainer<?> get(final OpenSearchContainer<?> candidate) {
        final var key = candidate.getDockerImageName() + (candidate.isSecurityEnabled() ? " (secured)" : "");
        final var existing = CONTAINERS.get(key);
        if (existing != null && existing.container().isRunning()) {
            return existing.container();
        }
        // note: a shared container may also have died, e.g. killed by the OOM killer of a small Docker VM while a test
        // ran a second cluster. it is then replaced, so that one such incident does not fail all subsequent tests.
        // test classes run one after another, thus keeping only one shared container alive at any time costs only a
        // few container starts but keeps the memory usage low (an OpenSearch container needs a lot of memory)
        stopAll();
        candidate.start();
        CONTAINERS.put(key, new SharedContainer(candidate, newAdminClient(candidate)));
        return candidate;
    }

    @SneakyThrows
    private static void stopAll() {
        for (final var shared : CONTAINERS.values()) {
            shared.adminClient()._transport().close();
            shared.container().stop();
        }
        CONTAINERS.clear();
    }

    /**
     * Deletes all (non-system) indices, so that every test starts with an empty cluster.
     */
    @SneakyThrows
    static synchronized void reset(final OpenSearchContainer<?> container) {
        final var shared = CONTAINERS.values().stream()
                .filter(c -> c.container() == container)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("not a shared container"));
        shared.adminClient().indices().delete(d -> d
                .index("*", "-.*")
                .expandWildcards(ExpandWildcard.Open, ExpandWildcard.Closed));
    }

    /**
     * @return a client which can talk to the container independent of its configuration (HTTP or HTTPS with a
     * self-signed certificate) and of the connection under test.
     */
    @SneakyThrows
    private static OpenSearchClient newAdminClient(final OpenSearchContainer<?> container) {
        final var host = HttpHost.create(container.getHttpHostAddress());
        final var sslContext = SSLContextBuilder.create().loadTrustMaterial(null, new TrustAllStrategy()).build();
        final var transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    if (container.getUsername() != null) {
                        final var credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(new AuthScope(host),
                                new UsernamePasswordCredentials(container.getUsername(), container.getPassword().toCharArray()));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    final var tlsStrategy = ClientTlsStrategyBuilder.create()
                            .setSslContext(sslContext)
                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                            // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
                            .setTlsDetailsFactory(sslEngine -> new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol()))
                            .build();
                    return httpClientBuilder
                            .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build());
                })
                .setMapper(new JacksonJsonpMapper())
                .build();
        return new OpenSearchClient(transport);
    }
}
