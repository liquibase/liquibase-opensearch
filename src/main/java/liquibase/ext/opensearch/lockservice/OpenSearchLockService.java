package liquibase.ext.opensearch.lockservice;

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.logging.Logger;
import liquibase.nosql.lockservice.AbstractNoSqlLockService;
import liquibase.util.NetUtil;
import org.apache.hc.core5.http.HttpStatus;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.transport.httpclient5.ResponseException;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OpenSearchLockService extends AbstractNoSqlLockService<OpenSearchLiquibaseDatabase> {

    /**
     * Magic ID: there will only ever be 0 or 1 entries in the lock index (we use the `create` API to ensure that it fails if the ID already exists)
     */
    private static final int LOCK_ENTRY_ID = 1;

    private final Logger log = Scope.getCurrentScope().getLog(getClass());

    private OpenSearchClient getOpenSearchClient() {
        final var connection = (OpenSearchConnection) this.getDatabase().getConnection();
        return connection.getOpenSearchClient();
    }

    @Override
    protected Logger getLogger() {
        return this.log;
    }

    @Override
    protected boolean existsRepository() throws DatabaseException {
        try {
            return this.getOpenSearchClient().indices().exists(r -> r.index(this.getDatabaseChangeLogLockTableName())).value();
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void createRepository() throws DatabaseException {
        // note: the mapping will be created in adjustRepository

        try {
            this.getOpenSearchClient().indices().create(r -> r.index(this.getDatabaseChangeLogLockTableName()));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void adjustRepository() throws DatabaseException {
        // properties must match DatabaseChangeLogLock
        final var request = new PutMappingRequest.Builder()
                .index(this.getDatabaseChangeLogLockTableName())
                .properties("id", p -> p.keyword(k -> k))
                .properties("lockGranted", p -> p.date(d -> d))
                .properties("lockedBy", p -> p.text(t -> t))
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
            this.getOpenSearchClient().indices().delete(r -> r.index(this.getDatabaseChangeLogLockTableName()));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected boolean isLocked() throws DatabaseException {
        return !this.queryLocks().isEmpty(); // ignore the fact that there should be exactly 0 or 1 entry here to be more conservative
    }

    @Override
    protected boolean createLock() throws DatabaseException {
        final var lockEntry = new DatabaseChangeLogLock(LOCK_ENTRY_ID, new Date(), getLockedBy());
        try {
            this.getOpenSearchClient()
                    .create(r -> r.index(this.getDatabaseChangeLogLockTableName())
                            .id(String.valueOf(LOCK_ENTRY_ID))
                            .document(lockEntry)
                            .refresh(Refresh.WaitFor));
        } catch (final ResponseException e) {
            if (e.status() == HttpStatus.SC_CONFLICT) {
                return false;
            }
            throw new DatabaseException(e);
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
        return true;
    }

    @Override
    protected void removeLock() throws DatabaseException {
        try {
            this.getOpenSearchClient()
                    .delete(r -> r.index(this.getDatabaseChangeLogLockTableName())
                            .id(String.valueOf(LOCK_ENTRY_ID))
                            .refresh(Refresh.WaitFor));
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected List<DatabaseChangeLogLock> queryLocks() throws DatabaseException {
        try {
            final var response = this.getOpenSearchClient()
                    .search(s -> s.index(this.getDatabaseChangeLogLockTableName()), DatabaseChangeLogLock.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
        } catch (final IOException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public boolean supports(final Database database) {
        return OpenSearchLiquibaseDatabase.PRODUCT_NAME.equals(database.getDatabaseProductName());
    }

    /**
     * Logic taken from {@code LockDatabaseChangeLogGenerator}
     *
     * @return the string to be used in the {@code lockedBy} field
     */
    private static String getLockedBy() {
        return String.format("%s%s (%s)",
                NetUtil.getLocalHostName(),
                Optional.ofNullable(System.getProperty("liquibase.hostDescription")).map(s -> '#' + s).orElse(""),
                NetUtil.getLocalHostAddress());
    }
}
