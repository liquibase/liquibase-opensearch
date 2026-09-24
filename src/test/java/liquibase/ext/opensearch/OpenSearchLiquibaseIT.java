package liquibase.ext.opensearch;

import liquibase.Scope;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeLogHistoryService;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.RanChangeSet;
import liquibase.command.CommandResults;
import liquibase.command.CommandScope;
import liquibase.command.core.ClearChecksumsCommandStep;
import liquibase.command.core.TagCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.DatabaseFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.report.UpdateReportParameters;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.search.Hit;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchLiquibaseIT extends AbstractOpenSearchLiquibaseIT {

    /**
     * Self-test of the test - if this fails something is wrong with the test environment.
     */
    @SneakyThrows
    @Test
    void openSearchIsRunning() {
        assertThat(this.getOpenSearchClient().info().clusterName()).isEqualTo("docker-cluster");
    }

    @SneakyThrows
    @Test
    void connectionReturnsClusterNameAndUuidUrl() {
        final var expectedUuid = this.getOpenSearchClient().info().clusterUuid();
        assertThat(this.connection.getURL())
                .isEqualTo("docker-cluster (%s)".formatted(expectedUuid));
    }

    @SneakyThrows
    @Test
    void itReturnsTheConnectionUserName() {
        assertThat(this.connection.getConnectionUserName()).isEqualTo(this.container.getUsername());
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
     * Liquibase caches the result of its "is the database up to date?" fast check
     * ({@link liquibase.changelog.FastCheckService}) for the whole JVM, using
     * {@link liquibase.database.DatabaseConnection#getURL()} as part of the cache key. If that value is not unique per
     * cluster then liquibase silently skips the update against a second cluster: it reports
     * "Database is up to date, no changesets to execute" without ever acquiring the lock or running a changeset.
     */
    @SneakyThrows
    @Test
    void itDoesNotReuseTheUpToDateCacheAcrossClusters() {
        // run it twice so that liquibase caches that this changelog has nothing left to run
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
        assertThat(this.indexExists("testindex")).isTrue();

        try (final var otherContainer = this.newContainer()) {
            otherContainer.start();

            this.database.close();
            this.connection.close();
            this.database = (OpenSearchLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(
                    "opensearch:" + otherContainer.getHttpHostAddress(),
                    otherContainer.getUsername(),
                    otherContainer.getPassword(),
                    null,
                    null);
            this.connection = (OpenSearchConnection) this.database.getConnection();

            // sanity check: this really is a different, empty cluster
            assertThat(this.indexExists("testindex")).isFalse();

            this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
            assertThat(this.indexExists("testindex")).isTrue();
        }
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
                        .refresh(Refresh.True));

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

    /**
     * Up to and including version 2.1.0 {@code orderExecuted} was never stored (the {@link RanChangeSet} constructor
     * used does not set it).
     */
    @SneakyThrows
    @Test
    void itStoresOrderExecuted() {
        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml"))).isEqualTo(2);
        assertThat(this.storedOrderExecuted()).containsExactlyElementsOf(range(1, 2));

        // re-running must not execute anything again
        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml"))).isZero();
        assertThat(this.loadRanChangeSets()).extracting(RanChangeSet::getOrderExecuted).containsExactlyElementsOf(range(1, 2));

        // the sequence continues after the highest stored value
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.yaml");
        assertThat(this.storedOrderExecuted()).containsExactlyElementsOf(range(1, 3));
    }

    /**
     * A plain search returns only 10 hits by default, so up to and including 2.1.0 only the first 10 changelog
     * entries were considered as "already executed".
     */
    @SneakyThrows
    @Test
    void itHandlesMoreThanTenChangeSets() {
        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.twelve-changesets.yaml"))).isEqualTo(12);
        assertThat(this.getDocumentCount("testindex-twelve")).isEqualTo(12);

        // re-running must not execute anything again
        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.twelve-changesets.yaml"))).isZero();
        assertThat(this.loadRanChangeSets()).extracting(RanChangeSet::getOrderExecuted).containsExactlyElementsOf(range(1, 12));
    }

    /**
     * The changelog index is loaded page-wise ({@link liquibase.ext.opensearch.database.OpenSearchSearchHelper#PAGE_SIZE}),
     * this ensures that the paging works with real changelog entries as they are stored by this extension.
     */
    @SneakyThrows
    @Test
    void itLoadsMoreThanOnePageOfChangelogEntries() {
        final var seeded = 1005;
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        this.seedRanChangeSets(seeded, false);

        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.twelve-changesets.yaml"))).isEqualTo(12);

        final var ranChangeSets = this.loadRanChangeSets();
        assertThat(ranChangeSets).hasSize(seeded + 12);
        assertThat(ranChangeSets).extracting(RanChangeSet::getOrderExecuted).containsExactlyElementsOf(range(1, seeded + 12));
    }

    /**
     * Up to and including version 2.1.0 {@code orderExecuted} was never stored (it's always {@code null}). Such legacy
     * entries must still all be loaded (even if they share the same {@code dateExecuted}) and must sort before any
     * new entries.
     */
    @SneakyThrows
    @Test
    void itLoadsLegacyChangelogEntriesWithoutOrderExecuted() {
        final var seeded = 1005;
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        this.seedRanChangeSets(seeded, true);

        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.twelve-changesets.yaml"))).isEqualTo(12);

        final var ranChangeSets = this.loadRanChangeSets();
        assertThat(ranChangeSets).hasSize(seeded + 12);
        assertThat(ranChangeSets.subList(0, seeded))
                .allSatisfy(r -> assertThat(r.getOrderExecuted()).isNull())
                .extracting(RanChangeSet::getId)
                .containsExactlyInAnyOrderElementsOf(range(1, seeded).stream().map(String::valueOf).toList());
        assertThat(ranChangeSets.subList(seeded, seeded + 12)).extracting(RanChangeSet::getOrderExecuted).containsExactlyElementsOf(range(1, 12));
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

    /**
     * A tag must be visible to subsequent reads right away, not only after the next scheduled refresh of the index.
     */
    @SneakyThrows
    @Test
    void itCanReadATagRightAfterTagging() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml");
        this.disableAutoRefresh(this.database.getDatabaseChangeLogTableName());

        this.tag("testTag");

        assertThat(this.historyService().tagExists("testTag")).isTrue();
    }

    /**
     * The checksums cached by the history service must be dropped when they are cleared.
     */
    @SneakyThrows
    @Test
    void itDropsCachedChecksumsWhenClearingThem() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml");
        assertThat(this.historyService().getRanChangeSets()).extracting(RanChangeSet::getLastCheckSum).doesNotContainNull();

        this.clearChecksums();

        assertThat(this.historyService().getRanChangeSets()).extracting(RanChangeSet::getLastCheckSum).containsOnlyNulls();
    }

    /**
     * Cleared checksums must be visible to subsequent reads right away, not only after the next scheduled refresh of
     * the index.
     */
    @SneakyThrows
    @Test
    void itCanReadClearedChecksumsRightAfterClearingThem() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml");
        this.disableAutoRefresh(this.database.getDatabaseChangeLogTableName());

        this.clearChecksums();

        assertThat(this.loadRanChangeSets()).isNotEmpty().extracting(RanChangeSet::getLastCheckSum).containsOnlyNulls();
    }

    /**
     * Entries executed within the same update run can share the same {@code dateExecuted}; the last one is identified
     * by {@code orderExecuted}.
     */
    @SneakyThrows
    @Test
    void itTagsTheEntryWithTheHighestOrderExecuted() {
        final var seeded = 20;
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        this.seedRanChangeSets(seeded, false);

        this.tag("testTag");

        assertThat(this.taggedEntries("testTag")).extracting(RanChangeSet::getOrderExecuted).containsExactly(seeded);
    }

    /**
     * Entries written by versions up to and including 2.1.0 have no {@code orderExecuted}; they must never win over
     * entries which have one, independent of their {@code dateExecuted}.
     */
    @SneakyThrows
    @Test
    void itDoesNotTagLegacyEntriesWithoutOrderExecuted() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.multiple-steps.yaml");
        // seeded after the update => newer dateExecuted than the real entries
        this.seedRanChangeSets(5, true);

        this.tag("testTag");

        assertThat(this.taggedEntries("testTag")).extracting(RanChangeSet::getOrderExecuted).containsExactly(2);
    }

    @SneakyThrows
    @Test
    void itSupportsAlternativeContentTypes() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.bulk.yaml");
        assertThat(this.indexExists("testindex")).isTrue();
        assertThat(this.getDocumentCount("testindex")).isEqualTo(2);
    }

    @SneakyThrows
    @Test
    void itExecutesADeleteRequestWithoutBody() {
        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.delete.yaml"))).isEqualTo(2);
        assertThat(this.indexExists("testindex-delete")).isFalse();
    }

    @SneakyThrows
    @Test
    void itExecutesADeleteRequestWithoutBodyWithXMLChangelog() {
        assertThat(this.executedChangeSetCount(this.doLiquibaseUpdate("liquibase/ext/changelog.httprequest.delete.xml"))).isEqualTo(2);
        assertThat(this.indexExists("xmltestindex-delete")).isFalse();
    }

    @Test
    void itFailsOnUnsupportedChangeTypes() {
        assertThatThrownBy(
                () -> this.doLiquibaseUpdate("liquibase/ext/changelog.unsupported-changetype.yaml")
        ).hasMessageContaining("Unknown type: liquibase.statement.core.CreateTableStatement");
    }

    private static List<Integer> range(final int fromInclusive, final int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive).boxed().toList();
    }

    private static int executedChangeSetCount(final CommandResults results) {
        return ((UpdateReportParameters) results.getResult("updateReport")).getChangesetInfo().getChangesetCount();
    }

    /**
     * @return the changelog entries as seen by liquibase (freshly loaded through the history service).
     */
    private List<RanChangeSet> loadRanChangeSets() throws Exception {
        final ChangeLogHistoryService historyService = this.historyService();
        // drop the list cached during the update so that the entries are really re-read from the index
        historyService.reset();
        return historyService.getRanChangeSets();
    }

    /**
     * @return the history service liquibase uses for {@link #database} (including its in-memory cache).
     */
    private ChangeLogHistoryService historyService() {
        return Scope.getCurrentScope().getSingleton(ChangeLogHistoryServiceFactory.class).getChangeLogService(this.database);
    }

    private void clearChecksums() throws Exception {
        new CommandScope(ClearChecksumsCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .execute();
    }

    /**
     * Disables the scheduled refresh of the index so that changes only become visible to searches if they explicitly
     * refresh the index.
     */
    private void disableAutoRefresh(final String index) throws Exception {
        this.getOpenSearchClient().indices().putSettings(p -> p
                .index(index)
                .settings(s -> s.refreshInterval(t -> t.time("-1"))));
    }

    /**
     * @return {@code orderExecuted} of all stored changelog entries, read directly from the index (independent of the
     * history service).
     */
    private List<Integer> storedOrderExecuted() throws Exception {
        final var index = this.database.getDatabaseChangeLogTableName();
        this.getOpenSearchClient().indices().refresh(r -> r.index(index));
        return this.getOpenSearchClient().search(s -> s
                                .index(index)
                                .size(100)
                                .sort(so -> so.field(f -> f.field("orderExecuted").order(SortOrder.Asc))),
                        RanChangeSet.class)
                .hits().hits().stream()
                .map(Hit::source)
                .map(RanChangeSet::getOrderExecuted)
                .toList();
    }

    private void tag(final String tag) throws Exception {
        new CommandScope(TagCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .addArgumentValue(TagCommandStep.TAG_ARG, tag)
                .execute();
    }

    /**
     * @return all stored changelog entries carrying the given tag, read directly from the index.
     */
    private List<RanChangeSet> taggedEntries(final String tag) throws Exception {
        final var index = this.database.getDatabaseChangeLogTableName();
        this.getOpenSearchClient().indices().refresh(r -> r.index(index));
        return this.getOpenSearchClient().search(s -> s
                                .index(index)
                                .size(100)
                                .query(q -> q.match(m -> m.field("tag").query(v -> v.stringValue(tag)))),
                        RanChangeSet.class)
                .hits().hits().stream()
                .map(Hit::source)
                .toList();
    }

    /**
     * Writes changelog entries directly into the index, all sharing the same {@code dateExecuted}.
     *
     * @param legacy if {@code true} the entries are written as versions up to and including 2.1.0 did: without {@code orderExecuted}.
     */
    private void seedRanChangeSets(final int count, final boolean legacy) throws Exception {
        final var dateExecuted = new Date();
        final var bulk = new BulkRequest.Builder().index(this.database.getDatabaseChangeLogTableName()).refresh(Refresh.True);
        IntStream.rangeClosed(1, count).forEach(i -> {
            final var ranChangeSet = new RanChangeSet("liquibase/ext/seeded.yaml", String.valueOf(i), "seed", null, dateExecuted, null, ChangeSet.ExecType.EXECUTED, "seeded", null, null, null, null, null);
            if (!legacy) {
                ranChangeSet.setOrderExecuted(i);
            }
            bulk.operations(op -> op.index(idx -> idx.id(ranChangeSet.toString()).document(ranChangeSet)));
        });
        assertThat(this.getOpenSearchClient().bulk(bulk.build()).errors()).isFalse();
    }

}
