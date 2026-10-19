package liquibase.ext.opensearch.database;

import liquibase.exception.DatabaseException;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchConnectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "::bad",
            "localhost:9200",
            "/just/a/path",
            "http://",
    })
    void itRejectsInvalidUrls(final String invalidUrl) {
        final var connection = new OpenSearchConnection();
        assertThatThrownBy(() -> connection.open("opensearch:http://localhost:9200," + invalidUrl, null, new Properties()))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Invalid OpenSearch URL '%s'".formatted(invalidUrl));
        assertThat(connection.isClosed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "opensearch:http://localhost:9200,https://other-host:9201",
            "opensearch:http://localhost:9200, https://other-host:9201",
    })
    @SneakyThrows
    void itAcceptsValidUrls(final String url) {
        // note: opening the connection does not contact OpenSearch yet
        final var connection = new OpenSearchConnection();
        try {
            connection.open(url, null, new Properties());
            assertThat(connection.getUris()).contains(List.of(URI.create("http://localhost:9200"), URI.create("https://other-host:9201")));
        } finally {
            connection.close();
        }
    }
}
