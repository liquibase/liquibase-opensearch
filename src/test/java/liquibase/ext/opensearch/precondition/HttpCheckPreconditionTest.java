package liquibase.ext.opensearch.precondition;

import com.sun.net.httpserver.HttpServer;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.PreconditionErrorException;
import liquibase.exception.PreconditionFailedException;
import liquibase.exception.ValidationErrors;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import lombok.SneakyThrows;
import org.apache.hc.core5.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.opensearch.client.opensearch.generic.Response;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCheckPreconditionTest {

    private static HttpCheckPrecondition precondition(final String method, final String path) {
        final var precondition = new HttpCheckPrecondition();
        precondition.setMethod(method);
        precondition.setPath(path);
        return precondition;
    }

    private static ValidationErrors validate(final HttpCheckPrecondition precondition) {
        return precondition.validate(new OpenSearchLiquibaseDatabase());
    }

    @Test
    void onlyThePathIsRequired() {
        final var precondition = new HttpCheckPrecondition();
        precondition.setPath("/testindex");
        assertThat(validate(precondition).getErrorMessages()).isEmpty();
        assertThat(precondition.getMethod()).isEqualTo("GET");
        assertThat(precondition.getExpectedStatusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST"})
    void itAcceptsMethodsWhichCanReadData(final String method) {
        assertThat(validate(precondition(method, "/testindex")).getErrorMessages()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "PATCH", "get"})
    void itRejectsOtherMethods(final String method) {
        assertThat(validate(precondition(method, "/testindex")).getErrorMessages())
                .containsExactly("method must be one of GET, HEAD, POST but was '%s'".formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankValuesAreReportedAsMissing(final String value) {
        assertThat(validate(precondition(value, value)).getErrorMessages()).containsExactlyInAnyOrder(
                "method is required for httpCheck",
                "path is required for httpCheck");
    }

    @Test
    void thePathMustStartWithASlash() {
        assertThat(validate(precondition("GET", "testindex")).getErrorMessages())
                .containsExactly("path must start with '/' but was 'testindex'");
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 600})
    void theExpectedStatusCodeMustBeAValidHttpStatusCode(final int statusCode) {
        final var precondition = precondition("GET", "/testindex");
        precondition.setExpectedStatusCode(statusCode);
        assertThat(validate(precondition).getErrorMessages())
                .containsExactly("expectedStatusCode must be between 100 and 599 but was %d".formatted(statusCode));
    }

    @Test
    void theExpectedStatusCodeIsRequired() {
        final var precondition = precondition("GET", "/testindex");
        precondition.setExpectedStatusCode(null);
        assertThat(validate(precondition).getErrorMessages()).containsExactly("expectedStatusCode is required for httpCheck");
    }

    @Test
    void aJsonPointerWithAnExpectedValueIsValid() {
        final var precondition = precondition("GET", "/testindex/_mapping");
        precondition.setJsonPointer("/testindex/mappings/properties/testfield/type");
        precondition.setExpectedValue("text");
        assertThat(validate(precondition).getErrorMessages()).isEmpty();
    }

    @Test
    void theJsonPointerMustStartWithASlash() {
        final var precondition = precondition("GET", "/testindex");
        precondition.setJsonPointer("testindex");
        precondition.setExpectedValue("foo");
        assertThat(validate(precondition).getErrorMessages())
                .containsExactly("jsonPointer must start with '/' but was 'testindex'");
    }

    @Test
    void theJsonPointerRequiresAnExpectedValue() {
        final var precondition = precondition("GET", "/testindex");
        precondition.setJsonPointer("/testindex");
        assertThat(validate(precondition).getErrorMessages()).containsExactly("expectedValue is required if jsonPointer is set");
    }

    @Test
    void theExpectedValueRequiresAJsonPointer() {
        final var precondition = precondition("GET", "/testindex");
        precondition.setExpectedValue("foo");
        assertThat(validate(precondition).getErrorMessages()).containsExactly("jsonPointer is required if expectedValue is set");
    }

    @Test
    void theJsonPointerCannotBeUsedWithHead() {
        final var precondition = precondition("HEAD", "/testindex");
        precondition.setJsonPointer("/testindex");
        precondition.setExpectedValue("foo");
        assertThat(validate(precondition).getErrorMessages())
                .containsExactly("jsonPointer can't be used with HEAD requests as their response has no body");
    }

    @Test
    void itReportsAllErrorsTogether() {
        assertThat(validate(precondition("PUT", "testindex")).getErrorMessages()).hasSize(2);
    }

    /**
     * A response with a different status code than expected usually means that the precondition does not hold. But
     * server errors and missing permissions mean that the state could not be checked at all, so they must be errors
     * (handled by `onError`), otherwise e.g. `onFail: MARK_RAN` would skip the changeset forever during an outage.
     * This must hold for the default transport (which throws an exception for most error status codes) as well as for
     * custom transports (which may return the response for every status code).
     */
    @Nested
    class Check {

        private static final String INFO_RESPONSE = """
                {
                  "name": "node", "cluster_name": "cluster", "cluster_uuid": "uuid", "tagline": "The OpenSearch Project",
                  "version": {
                    "distribution": "opensearch", "number": "3.0.0", "build_type": "tar", "build_hash": "hash",
                    "build_date": "2025-01-01T00:00:00Z", "build_snapshot": false, "lucene_version": "10.1.0",
                    "minimum_wire_compatibility_version": "2.19.0", "minimum_index_compatibility_version": "2.0.0"
                  }
                }
                """;

        private HttpServer server;
        private OpenSearchTransport transport;
        private OpenSearchLiquibaseDatabase database;
        private final DatabaseChangeLog changeLog = new DatabaseChangeLog("changelog.yaml");

        @SneakyThrows
        @BeforeEach
        void startServer() {
            // responds with the status code given as the last path segment, e.g. `/status/503` => 503
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.server.createContext("/status/", exchange -> {
                final var path = exchange.getRequestURI().getPath();
                exchange.sendResponseHeaders(Integer.parseInt(path.substring(path.lastIndexOf('/') + 1)), -1);
                exchange.close();
            });
            // liquibase checks the version of OpenSearch when setting the connection
            this.server.createContext("/", exchange -> {
                final var info = INFO_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, info.length);
                exchange.getResponseBody().write(info);
                exchange.close();
            });
            this.server.start();

            final var host = new HttpHost("http", this.server.getAddress().getHostString(), this.server.getAddress().getPort());
            this.transport = ApacheHttpClient5TransportBuilder.builder(host).build();
            this.database = new OpenSearchLiquibaseDatabase();
            this.database.setConnection(new OpenSearchConnection(new OpenSearchClient(this.transport)));
        }

        @SneakyThrows
        @AfterEach
        void stopServer() {
            this.transport.close();
            this.server.stop(0);
        }

        private void check(final int expectedStatusCode, final int actualStatusCode) throws Exception {
            final var precondition = precondition("GET", "/status/" + actualStatusCode);
            precondition.setExpectedStatusCode(expectedStatusCode);
            precondition.check(this.database, this.changeLog, null, null);
        }

        /**
         * Uses a custom transport for the request of the precondition which - unlike the default
         * `ApacheHttpClient5Transport` - returns the response for every status code instead of throwing an exception.
         */
        private void checkWithCustomTransport(final int expectedStatusCode, final int actualStatusCode) throws Exception {
            final var client = new OpenSearchClient(this.transport) {
                @Override
                public OpenSearchGenericClient generic() {
                    return new OpenSearchGenericClient(new StatusTransport(actualStatusCode));
                }
            };
            final var customDatabase = new OpenSearchLiquibaseDatabase();
            customDatabase.setConnection(new OpenSearchConnection(client));

            final var precondition = precondition("GET", "/testindex");
            precondition.setExpectedStatusCode(expectedStatusCode);
            precondition.check(customDatabase, this.changeLog, null, null);
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 400, 404})
        void itHoldsIfTheStatusCodeIsTheExpectedOne(final int statusCode) {
            assertThatCode(() -> this.check(statusCode, statusCode)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 409, 503})
        void itHoldsIfTheStatusCodeIsTheExpectedOneWithACustomTransport(final int statusCode) {
            assertThatCode(() -> this.checkWithCustomTransport(statusCode, statusCode)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 400, 404, 405})
        void itFailsOnAnotherStatusCode(final int statusCode) {
            final var expectedStatusCode = statusCode == 200 ? 404 : 200;
            assertFailedWithStatus(() -> this.check(expectedStatusCode, statusCode), expectedStatusCode, statusCode);
        }

        @ParameterizedTest
        @ValueSource(ints = {200, 302, 404, 409})
        void itFailsOnAnotherStatusCodeWithACustomTransport(final int statusCode) {
            final var expectedStatusCode = statusCode == 200 ? 404 : 200;
            assertFailedWithStatus(() -> this.checkWithCustomTransport(expectedStatusCode, statusCode), expectedStatusCode, statusCode);
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403, 500, 503})
        void itIsAnErrorIfTheStateCouldNotBeChecked(final int statusCode) {
            assertThatThrownBy(() -> this.check(404, statusCode)).isInstanceOf(PreconditionErrorException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403, 500, 503})
        void itIsAnErrorIfTheStateCouldNotBeCheckedWithACustomTransport(final int statusCode) {
            assertThatThrownBy(() -> this.checkWithCustomTransport(404, statusCode))
                    .isInstanceOf(PreconditionErrorException.class)
                    .hasStackTraceContaining("expected HTTP status 404 but got %d".formatted(statusCode));
        }

        private static void assertFailedWithStatus(final ThrowingCallable callable, final int expectedStatusCode, final int actualStatusCode) {
            assertThatThrownBy(callable)
                    .asInstanceOf(InstanceOfAssertFactories.type(PreconditionFailedException.class))
                    .extracting(e -> e.getFailedPreconditions().get(0).getMessage())
                    .asString().endsWith("expected HTTP status %d but got %d".formatted(expectedStatusCode, actualStatusCode));
        }
    }

    /**
     * Transport which answers every request with an empty response with the given status code.
     */
    private record StatusTransport(int status) implements OpenSearchTransport {

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(final RequestT request,
                                                                      final Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                                                                      final TransportOptions options) {
            return (ResponseT) new StatusResponse(this.status);
        }

        @Override
        public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(final RequestT request,
                                                                                              final Endpoint<RequestT, ResponseT, ErrorT> endpoint,
                                                                                              final TransportOptions options) {
            return CompletableFuture.completedFuture(this.performRequest(request, endpoint, options));
        }

        @Override
        public JsonpMapper jsonpMapper() {
            return new JacksonJsonpMapper();
        }

        @Override
        public TransportOptions options() {
            return null;
        }

        @Override
        public void close() {
            // nothing to close
        }
    }

    private record StatusResponse(int status) implements Response {

        @Override
        public Optional<Body> getBody() {
            return Optional.empty();
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getReason() {
            return "";
        }

        @Override
        public int getStatus() {
            return this.status;
        }

        @Override
        public String getUri() {
            return "/";
        }

        @Override
        public Collection<Map.Entry<String, String>> getHeaders() {
            return List.of();
        }

        @Override
        public void close() {
            // nothing to close
        }
    }
}
