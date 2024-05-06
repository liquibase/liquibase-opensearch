package liquibase.ext.opensearch.changelog;

import liquibase.ChecksumVersion;
import liquibase.Scope;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.RanChangeSet;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.logging.Logger;
import liquibase.nosql.changelog.AbstractNoSqlHistoryService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.mapping.*;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.PutMappingRequest;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OpenSearchHistoryService extends AbstractNoSqlHistoryService<OpenSearchLiquibaseDatabase> {

    private final Logger log = Scope.getCurrentScope().getLog(getClass());

    @Override
    protected Logger getLogger() {
        return log;
    }

    private OpenSearchClient getOpenSearchClient() {
        final var connection = (OpenSearchConnection) this.getNoSqlDatabase().getConnection();
        return connection.getOpenSearchClient();
    }

    @Override
    protected boolean existsRepository() throws DatabaseException {
        try {
            return this.getOpenSearchClient().indices().exists(r -> r.index(this.getDatabaseChangeLogTableName())).value();
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void createRepository() throws DatabaseException {
        // note: the mapping will be created in adjustRepository

        try {
            this.getOpenSearchClient().indices().create(r -> r.index(this.getDatabaseChangeLogTableName()));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void adjustRepository() throws DatabaseException {
        // properties must match RanChangeSet & CheckSum & ContextExpression (validated by matching tests)
        final var request = new PutMappingRequest.Builder()
                .index(this.getDatabaseChangeLogTableName())
                .properties("id", p -> p.keyword(k -> k))
                .properties("changeLog", p -> p.keyword(k -> k))
                .properties("storedChangeLog", p -> p.keyword(k -> k))
                .properties("author", p -> p.text(t -> t))
                .properties("lastCheckSum", p -> p.object(o -> {
                            o.properties("version", p2 -> p2.integer(i -> i));
                            o.properties("storedCheckSum", p2 -> p2.keyword(k -> k));
                            return o;
                        }))
                .properties("dateExecuted", p -> p.date(d -> d))
                .properties("tag", p -> p.text(t -> t))
                .properties("execType", p -> p.keyword(k -> k))
                .properties("description", p -> p.text(t -> t))
                .properties("comments", p -> p.text(t -> t))
                .properties("orderExecuted", p -> p.integer(i -> i))
                .properties("contextExpression", p -> p.object(o -> {
                    o.properties("contexts", p2 -> p2.keyword(k -> k));
                    o.properties("originalString", p2 -> p2.text(t -> t));
                    return o;
                }))
                .properties("labels", p -> p.text(t -> t))
                .properties("deploymentId", p -> p.text(t -> t))
                .properties("liquibaseVersion", p -> p.text(t -> t))
                .build();

        try {
            this.getOpenSearchClient().indices().putMapping(request);
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void dropRepository() throws DatabaseException {
        try {
            this.getOpenSearchClient().indices().delete(r -> r.index(this.getDatabaseChangeLogTableName()));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected List<RanChangeSet> queryRanChangeSets() throws DatabaseException {
        try {
            final var response = this.getOpenSearchClient()
                    .search(s -> s.index(this.getDatabaseChangeLogTableName()), RanChangeSet.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected int generateNextSequence() throws DatabaseException {
        final var aggregationName = "max";
        final var request = new SearchRequest.Builder()
                .index(this.getDatabaseChangeLogTableName())
                .aggregations(aggregationName, a -> a.max(m -> m.field("orderExecuted")))
                .build();
        try {
            final var response = this.getOpenSearchClient().search(request, RanChangeSet.class);
            return (int) response.aggregations().get(aggregationName).max().value();
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void markChangeSetRun(final ChangeSet changeSet, final ChangeSet.ExecType execType, final Integer nextSequenceValue) throws DatabaseException {
        final var ranChangeSet = new RanChangeSet(changeSet, execType, null, null);

        try {
            this.getOpenSearchClient()
                    .index(r -> r.index(this.getDatabaseChangeLogTableName())
                            .id(ranChangeSet.getId())
                            .document(ranChangeSet)
                            .refresh(Refresh.WaitFor));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void removeRanChangeSet(final ChangeSet changeSet) throws DatabaseException {
        try {
            this.getOpenSearchClient()
                    .delete(r -> r.index(this.getDatabaseChangeLogTableName())
                            .id(String.valueOf(changeSet.getId()))
                            .refresh(Refresh.WaitFor));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public void clearAllCheckSums() throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected long countTags(final String tag) throws DatabaseException {
        final var request = new SearchRequest.Builder()
                .index(this.getDatabaseChangeLogTableName())
                .query(q -> q.match(m -> m.field("tag").query(FieldValue.of(tag))))
                .build();
        try {
            final var response = this.getOpenSearchClient().search(request, RanChangeSet.class);
            return response.hits().total().value();
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void tagLast(final String tagString) throws DatabaseException {
        // TODO
    }

    @Override
    protected long countRanChangeSets() throws DatabaseException {
        return this.queryRanChangeSets().size();
    }

    @Override
    protected void updateCheckSum(final ChangeSet changeSet) throws DatabaseException {
        @AllArgsConstructor
        @Getter
        class CheckSumObj {
            final CheckSum lastCheckSum;
        }
        final var currentChecksumVersion = Optional.ofNullable(changeSet.getStoredCheckSum())
                .map(cs -> ChecksumVersion.enumFromChecksumVersion(cs.getVersion()))
                .orElse(ChecksumVersion.latest());
        final var checkSum = changeSet.generateCheckSum(currentChecksumVersion);

        try {
            this.getOpenSearchClient()
                    .update(r -> r.index(this.getDatabaseChangeLogTableName())
                                    .id(changeSet.getId())
                                    .doc(new CheckSumObj(checkSum))
                                    .refresh(Refresh.WaitFor)
                            , RanChangeSet.class);
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public boolean supports(final Database database) {
        return OpenSearchLiquibaseDatabase.PRODUCT_NAME.equals(database.getDatabaseProductName());
    }

    @Override
    public boolean isDatabaseChecksumsCompatible() {
        return true;
    }

}
