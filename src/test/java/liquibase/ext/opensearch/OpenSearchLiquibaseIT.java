package liquibase.ext.opensearch;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSearchLiquibaseIT extends AbstractOpenSearchLiquibaseIT {

    /**
     * Self-test of the test - if this fails something is wrong with the test environment.
     */
    @SneakyThrows
    @Test
    public void openSearchIsRunning() {
        assertThat(this.getOpenSearchClient().info().clusterName()).isEqualTo("docker-cluster");
        assertThat(this.database.getDatabaseMajorVersion()).isEqualTo(2);
    }

}
