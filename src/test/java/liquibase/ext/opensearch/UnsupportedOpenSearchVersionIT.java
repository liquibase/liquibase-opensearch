package liquibase.ext.opensearch;

import liquibase.database.DatabaseFactory;
import liquibase.exception.UnexpectedLiquibaseException;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unsupported OpenSearch versions must be rejected up front instead of failing with an obscure error later on.
 */
@Testcontainers
class UnsupportedOpenSearchVersionIT {

    @Container
    private final OpenSearchContainer<?> container = new OpenSearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:1.3.20"))
            // the default heap (1 GB) is far more than needed; it also runs next to the shared container of other ITs
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m");

    @Test
    void itRejectsOpenSearchOlderThan2_0() {
        assertThatThrownBy(() -> DatabaseFactory.getInstance().openDatabase(
                "opensearch:" + this.container.getHttpHostAddress(),
                this.container.getUsername(),
                this.container.getPassword(),
                null,
                null))
                .isInstanceOf(UnexpectedLiquibaseException.class)
                .hasMessageContaining("OpenSearch 2.0 or newer is required")
                .hasMessageContaining("1.3");
    }
}
