package liquibase.ext.opensearch;

import liquibase.command.CommandScope;
import liquibase.command.core.ClearChecksumsCommandStep;
import liquibase.command.core.TagCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchLiquibaseIT extends AbstractOpenSearchLiquibaseIT {

    /**
     * Self-test of the test - if this fails something is wrong with the test environment.
     */
    @SneakyThrows
    @Test
    void openSearchIsRunning() {
        assertThat(this.getOpenSearchClient().info().clusterName()).isEqualTo("docker-cluster");
        assertThat(this.database.getDatabaseMajorVersion()).isEqualTo(2);
    }

    @Test
    void connectionReturnsClusterNameAsUrl() {
        assertThat(this.connection.getURL()).isEqualTo("docker-cluster");
    }

    @SneakyThrows
    @Test
    void itCreatesTheChangelogAndLockIndices() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        assertThat(this.indexExists(this.database.getDatabaseChangeLogLockTableName())).isTrue();
        assertThat(this.indexExists(this.database.getDatabaseChangeLogTableName())).isTrue();
    }

    @SneakyThrows
    @Test
    void itExecutesAHttpRequestAndCreatesTheIndexWithYAMLChangelog() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
        assertThat(this.indexExists("testindex")).isTrue();
    }

    @SneakyThrows
    @Test
    void itExecutesAHttpRequestAndCreatesTheIndexWithXMLChangelog() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.xml");
        assertThat(this.indexExists("xmltestindex")).isTrue();
    }

    @SneakyThrows
    @Test
    void itHandlesReRuns() {
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
    void itRespectsTheContextFilter() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.contexts.yaml", "context1");
        assertThat(this.indexExists("testindex1")).isTrue();
        assertThat(this.indexExists("testindex2")).isFalse();
    }

    @SneakyThrows
    @Test
    void itCanClearAllChecksums() {
        // run at least one change set
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");

        final var countBeforeClear = this.getDocumentCount("databasechangelog", new Query.Builder().exists(e -> e.field("lastCheckSum")).build());
        assertThat(countBeforeClear).isNotZero();

        new CommandScope(ClearChecksumsCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .execute();

        final var countAfterClear = this.getDocumentCount("databasechangelog", new Query.Builder().exists(e -> e.field("lastCheckSum")).build());
        assertThat(countAfterClear).isZero();
    }

    @SneakyThrows
    @Test
    void itCanTagEntries() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml");

        final var countBeforeTag = this.getDocumentCount("databasechangelog", new Query.Builder().exists(e -> e.field("tag")).build());
        assertThat(countBeforeTag).isZero();

        new CommandScope(TagCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .addArgumentValue(TagCommandStep.TAG_ARG, "testTag")
                .execute();

        // ensure that we have exactly one tag set
        final var countAfterTag = this.getDocumentCount("databasechangelog", new Query.Builder()
                .match(m -> m.field("tag").query(v -> v.stringValue("testTag"))).build());
        assertThat(countAfterTag).isEqualTo(1);

        // we know that ID=4001 is the last, so it must be this one which has been tagged
        final var countAfterTagWithId4001 = this.getDocumentCount("databasechangelog", new Query.Builder()
                .bool(
                        b -> b.must(
                                new Query.Builder().match(
                                        m -> m.field("tag").query(v -> v.stringValue("testTag"))
                                ).build(),
                                new Query.Builder().ids(
                                        i -> i.values("4001")
                                ).build()
                        )).build());
        assertThat(countAfterTagWithId4001).isEqualTo(1);
    }

}
