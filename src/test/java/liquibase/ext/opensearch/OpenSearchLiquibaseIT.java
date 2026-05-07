package liquibase.ext.opensearch;

import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.RanChangeSet;
import liquibase.command.CommandScope;
import liquibase.command.core.ClearChecksumsCommandStep;
import liquibase.command.core.TagCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.report.ChangesetInfo;
import liquibase.report.UpdateReportParameters;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchLiquibaseIT extends AbstractOpenSearchLiquibaseIT {

    /**
     * Self-test of the test - if this fails something is wrong with the test environment.
     */
    @SneakyThrows
    @Test
    void openSearchIsRunning() {
        assertThat(this.getOpenSearchClient().info().clusterName()).isEqualTo("docker-cluster");
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

    /**
     * Up to and including version 0.2.0 we stored only the `id` as the ID of a document instead of path+id+author.
     * This test ensures that we still match old entries and don't re-run them.
     */
    @SneakyThrows
    @Test
    void itSkipsPreviouslyExecutedChangelogEntries() {
        // first run an empty changelog so that liquibase sets up the changelog index
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");

        // then simulate that this ran with an earlier version (with the old ID handling)
        final var ranChangeSet = new RanChangeSet("liquibase/ext/changelog.httprequest.yaml", "1", "test", CheckSum.parse("9:8f8ad33ca7428632a913f3295bb18900"), new Date(), "", ChangeSet.ExecType.EXECUTED, "httpRequest path=/testindex", "httpRequestComment", null, null, "");
        this.getOpenSearchClient()
                .index(r -> r.index("databasechangelog")
                        .id(ranChangeSet.getId()) // use getId instead of toString to simulate old behaviour
                        .document(ranChangeSet)
                        .refresh(Refresh.WaitFor));

        // now run the changelog - the index is not supposed to be created
        final var updateResult = this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
        assertThat(this.indexExists("testindex")).isFalse();
        final var updateReport = ((UpdateReportParameters) updateResult.getResult("updateReport")).getChangesetInfo();
        assertThat(updateReport.getChangesetCount()).isEqualTo(0);
    }

    @SneakyThrows
    @Test
    void itSkipsExecutedChangelogEntries() {
        // run it the first time (expected to execute it)
        this.doLiquibaseUpdate("liquibase/ext/changelog.id-handling.yaml");
        assertThat(this.indexExists("testindex")).isTrue();
        assertThat(this.indexExists("testindex2")).isTrue();
        // run it a second time (expected to succeed and not re-run the script again)
        final var updateResult = this.doLiquibaseUpdate("liquibase/ext/changelog.id-handling.yaml");
        final var updateReport = ((UpdateReportParameters) updateResult.getResult("updateReport")).getChangesetInfo();
        assertThat(updateReport.getChangesetCount()).isEqualTo(0);
    }

    @SneakyThrows
    @Test
    void itHandlesChangelogsWithIncludes() {
        // run it the first time (expected to execute it)
        this.doLiquibaseUpdate("liquibase/ext/changelog.multi-include.yaml");
        assertThat(this.indexExists("testindex")).isTrue();
        assertThat(this.indexExists("testindex1")).isTrue();
        assertThat(this.indexExists("testindex2")).isTrue();
        // run it a second time (expected to succeed and not re-run the script again)
        final var updateResult = this.doLiquibaseUpdate("liquibase/ext/changelog.multi-include.yaml");
        final var updateReport = ((UpdateReportParameters) updateResult.getResult("updateReport")).getChangesetInfo();
        assertThat(updateReport.getChangesetCount()).isEqualTo(0);
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

        final var countBeforeClear = this.getDocumentCount("databasechangelog", Query.of(q -> q.exists(e -> e.field("lastCheckSum"))));
        assertThat(countBeforeClear).isNotZero();

        new CommandScope(ClearChecksumsCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .execute();

        final var countAfterClear = this.getDocumentCount("databasechangelog", Query.of(q -> q.exists(e -> e.field("lastCheckSum"))));
        assertThat(countAfterClear).isZero();
    }

    @SneakyThrows
    @Test
    void itCanTagEntries() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml");

        final var countBeforeTag = this.getDocumentCount("databasechangelog", Query.of(q -> q.exists(e -> e.field("tag"))));
        assertThat(countBeforeTag).isZero();

        new CommandScope(TagCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .addArgumentValue(TagCommandStep.TAG_ARG, "testTag")
                .execute();

        // ensure that we have exactly one tag set
        final var countAfterTag = this.getDocumentCount("databasechangelog", Query.of(q ->
                q.match(m -> m.field("tag").query(v -> v.stringValue("testTag")))));
        assertThat(countAfterTag).isEqualTo(1);

        // we know that ID=2 is the last, so it must be this one which has been tagged
        final var countAfterTagWithId2 = this.getDocumentCount("databasechangelog", Query.of(q ->
                q.bool(b ->
                    b.must(
                        m -> m.match(
                            ma -> ma.field("tag").query(v -> v.stringValue("testTag"))
                        )
                    )
                    .must(
                        m -> m.match(
                            ma -> ma.field("id").query(v -> v.stringValue("2"))
                        )
                    )
                )));
        assertThat(countAfterTagWithId2).isEqualTo(1);
    }

    @SneakyThrows
    @Test
    void itSupportsAlternativeContentTypes() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.bulk.yaml");
        assertThat(this.indexExists("testindex")).isTrue();
        assertThat(this.getDocumentCount("testindex")).isEqualTo(2);
    }

}
