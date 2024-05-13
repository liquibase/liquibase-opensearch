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

    @SneakyThrows
    @Test
    public void itCreatesTheChangelogAndLockIndices() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        assertThat(this.indexExists(this.database.getDatabaseChangeLogLockTableName())).isTrue();
        assertThat(this.indexExists(this.database.getDatabaseChangeLogTableName())).isTrue();
    }

    @SneakyThrows
    @Test
    public void itExecutesAHttpRequestAndCreatesTheIndex() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
        assertThat(this.indexExists("testindex")).isTrue();
    }

    @SneakyThrows
    @Test
    public void itHandlesReRuns() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.always.yaml");
        assertThat(this.indexExists("testindex-always")).isTrue();
        assertThat(this.getDocumentCount("testindex-always")).isEqualTo(1);
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.always.yaml");
        assertThat(this.getDocumentCount("testindex-always")).isEqualTo(2);
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.always.yaml");
        assertThat(this.getDocumentCount("testindex-always")).isEqualTo(3);
    }

    @SneakyThrows
    @Test
    public void itRespectsTheContextFilter() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.contexts.yaml", "context1");
        assertThat(this.indexExists("testindex1")).isTrue();
        assertThat(this.indexExists("testindex2")).isFalse();
    }

}
