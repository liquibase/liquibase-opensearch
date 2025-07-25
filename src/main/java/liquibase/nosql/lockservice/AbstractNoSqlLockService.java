package liquibase.nosql.lockservice;

/*-
 * #%L
 * Liquibase NoSql Extension
 * %%
 * Copyright (C) 2020 Mastercard
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import liquibase.configuration.GlobalConfiguration;
import liquibase.configuration.LiquibaseConfiguration;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.LockException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.lockservice.LockService;
import liquibase.logging.Logger;
import liquibase.nosql.database.AbstractNoSqlDatabase;
import lombok.Getter;
import lombok.Setter;

import java.text.DateFormat;
import java.time.Clock;
import java.util.List;
import java.util.ResourceBundle;

import static java.util.Objects.isNull;
import static liquibase.plugin.Plugin.PRIORITY_SPECIALIZED;

public abstract class AbstractNoSqlLockService<D extends AbstractNoSqlDatabase> implements LockService {

    private D database;

    private boolean hasChangeLogLock;

    private Long changeLogLockPollRate;

    private Long changeLogLockRecheckTime;

    @Getter
    private Boolean hasDatabaseChangeLogLockTable;

    @Getter
    private Boolean adjustedChangeLogLockTable = false;

    /**
     * Clock field in order to make it testable
     */
    @Getter
    @Setter
    private Clock clock = Clock.systemDefaultZone();

    @Override
    public int getPriority() {
        return PRIORITY_SPECIALIZED;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setDatabase(final Database database) {
        this.database = (D) database;
    }

    public D getDatabase() {
        return database;
    }

    @Override
    public void init() throws DatabaseException {
        if (!hasDatabaseChangeLogLockTable()) {
            getLogger().info("Create Database Lock Collection: " + getDatabaseChangeLogLockTableName());
            createRepository();
            database.commit();
            getLogger().info("Created database lock Collection: " + getDatabaseChangeLogLockTableName());
            this.hasDatabaseChangeLogLockTable = true;
        }
        if (!adjustedChangeLogLockTable) {
            adjustRepository();
            adjustedChangeLogLockTable = true;
        }
    }

    @Override
    public boolean hasChangeLogLock() {
        return hasChangeLogLock;
    }

    @Override
    public void waitForLock() throws LockException {

        boolean locked = false;

        final long timeToGiveUp = getClock().instant().plusSeconds(getChangeLogLockWaitTime() * 60).toEpochMilli();
        while (!locked && (getClock().instant().toEpochMilli() < timeToGiveUp)) {
            locked = acquireLock();
            if (!locked) {
                getLogger().info("Waiting for changelog lock....");
                try {
                    //noinspection BusyWait
                    Thread.sleep(getChangeLogLockRecheckTime() * 1000);
                } catch (InterruptedException e) {
                    // Restore thread interrupt status
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!locked) {
            DatabaseChangeLogLock[] locks = listLocks();
            String lockedBy;
            if (locks.length > 0) {
                DatabaseChangeLogLock lock = locks[0];
                lockedBy = lock.getLockedBy() + " since " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(lock.getLockGranted());
            } else {
                lockedBy = "UNKNOWN";
            }
            throw new LockException("Could not acquire change log lock.  Currently locked by " + lockedBy);
        }
    }

    @Override
    public boolean acquireLock() throws LockException {
        if (hasChangeLogLock) {
            return true;
        }

        try {
            database.rollback();
            this.init();

            if (isLocked()) {
                return false;
            } else {
                getLogger().info("Lock Database");

                if (!createLock()) {
                    // another node was faster
                    return false;
                }

                database.commit();
                getLogger().info("Successfully Acquired Change Log Lock");

                this.hasChangeLogLock = true;

                return true;
            }
        } catch (final Exception e) {
            throw new LockException(e);
        } finally {
            try {
                database.rollback();
            } catch (final DatabaseException e) {
                getLogger().severe("Error on acquire change log lock Rollback.", e);
            }
        }
    }

    @Override
    public void releaseLock() throws LockException {

        try {
            if (hasDatabaseChangeLogLockTable()) {

                getLogger().info("Release Database Lock");

                database.rollback();
                removeLock();
                database.commit();
            }
        } catch (Exception e) {
            throw new LockException(e);
        } finally {
            try {
                this.hasChangeLogLock = false;
                database.setCanCacheLiquibaseTableInfo(false);
                getLogger().info("Successfully released change log lock");
                database.rollback();
            } catch (DatabaseException e) {
                getLogger().severe("Error on released change log lock Rollback.", e);
            }
        }
    }

    @Override
    public DatabaseChangeLogLock[] listLocks() throws LockException {
        try {
            if (!this.hasDatabaseChangeLogLockTable()) {
                return new DatabaseChangeLogLock[0];
            }
            final List<DatabaseChangeLogLock> rows = queryLocks();
            return rows.stream().map(DatabaseChangeLogLock.class::cast).toArray(DatabaseChangeLogLock[]::new);
        } catch (final Exception e) {
            throw new LockException(e);
        }
    }

    @Override
    public void forceReleaseLock() throws LockException, DatabaseException {
        init();
        releaseLock();
    }

    @Override
    public void reset() {
        hasChangeLogLock = false;
        hasDatabaseChangeLogLockTable = null;
        adjustedChangeLogLockTable = false;
    }

    @Override
    public void destroy() {
        try {
            getLogger().info("Dropping Collection Database Change Log Lock: " + getDatabaseChangeLogLockTableName());
            dropRepository();
            getLogger().info("Dropped Collection Database Change Log Lock: " + getDatabaseChangeLogLockTableName());
            database.commit();
            reset();
        } catch (final DatabaseException e) {
            throw new UnexpectedLiquibaseException(e);
        }
    }

    public String getDatabaseChangeLogLockTableName() {
        return database.getDatabaseChangeLogLockTableName();
    }

    public Long getChangeLogLockRecheckTime() {
        if (changeLogLockRecheckTime != null) {
            return changeLogLockRecheckTime;
        }
        return LiquibaseConfiguration
                .getInstance()
                .getConfiguration(GlobalConfiguration.class)
                .getDatabaseChangeLogLockPollRate();
    }

    @Override
    public void setChangeLogLockRecheckTime(long changeLogLockRecheckTime) {
        this.changeLogLockRecheckTime = changeLogLockRecheckTime;
    }

    public Long getChangeLogLockWaitTime() {
        if (changeLogLockPollRate != null) {
            return changeLogLockPollRate;
        }
        return LiquibaseConfiguration
                .getInstance()
                .getConfiguration(GlobalConfiguration.class)
                .getDatabaseChangeLogLockWaitTime();
    }

    @Override
    public void setChangeLogLockWaitTime(long changeLogLockWaitTime) {
        this.changeLogLockPollRate = changeLogLockWaitTime;
    }

    private boolean hasDatabaseChangeLogLockTable() throws DatabaseException {
        if (isNull(this.hasDatabaseChangeLogLockTable)) {
            try {
                this.hasDatabaseChangeLogLockTable =
                        existsRepository();
            } catch (final Exception e) {
                throw new DatabaseException(e);
            }
        }
        return this.hasDatabaseChangeLogLockTable;
    }

    protected abstract Logger getLogger();

    protected abstract boolean existsRepository() throws DatabaseException;

    protected abstract void createRepository() throws DatabaseException;

    protected abstract void adjustRepository() throws DatabaseException;

    protected abstract void dropRepository() throws DatabaseException;

    protected abstract boolean isLocked() throws DatabaseException;

    protected abstract boolean createLock() throws DatabaseException;

    protected abstract void removeLock() throws DatabaseException;

    protected abstract List<DatabaseChangeLogLock> queryLocks() throws DatabaseException;

}
