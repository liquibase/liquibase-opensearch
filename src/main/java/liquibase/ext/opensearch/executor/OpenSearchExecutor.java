package liquibase.ext.opensearch.executor;

/*-
 * #%L
 * Liquibase CosmosDB Extension
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

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.executor.AbstractExecutor;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.ext.opensearch.statement.OpenSearchExecuteStatement;
import liquibase.logging.Logger;
import liquibase.servicelocator.LiquibaseService;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.statement.SqlStatement;
import lombok.NoArgsConstructor;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.OpenSearchClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

@LiquibaseService
@NoArgsConstructor
public class OpenSearchExecutor extends AbstractExecutor {

    public static final String EXECUTOR_NAME = "jdbc"; // needed because of AbstractJdbcDatabase#execute
    private final Logger log = Scope.getCurrentScope().getLog(getClass());

    @Override
    public void setDatabase(final Database database) {
        super.setDatabase(database);
    }

    private OpenSearchLiquibaseDatabase getDatabase() {
        return (OpenSearchLiquibaseDatabase)this.database;
    }

    @Override
    public String getName() {
        return EXECUTOR_NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY_SPECIALIZED;
    }

    @Override
    public boolean supports(final Database database) {
        return OpenSearchLiquibaseDatabase.PRODUCT_NAME.equals(database.getDatabaseProductName());
    }

    @Override
    public <T> T queryForObject(final SqlStatement sql, final Class<T> requiredType) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T queryForObject(final SqlStatement sql, final Class<T> requiredType, final List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long queryForLong(final SqlStatement sql) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long queryForLong(final SqlStatement sql, final List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int queryForInt(final SqlStatement sql) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int queryForInt(final SqlStatement sql, final List<SqlVisitor> sqlVisitors) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Object> queryForList(final SqlStatement sql, final Class elementType) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> queryForList(final SqlStatement sql, final Class elementType, final List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, ?>> queryForList(final SqlStatement sql) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Map<String, ?>> queryForList(final SqlStatement sql, final List<SqlVisitor> sqlVisitors) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(final SqlStatement sql) throws DatabaseException {
        this.execute(sql, emptyList());
    }

    @Override
    public void execute(final SqlStatement sql, final List<SqlVisitor> sqlVisitors) throws DatabaseException {
        if (sql instanceof OpenSearchExecuteStatement) {
            try {
                ((OpenSearchExecuteStatement) sql).execute(getDatabase());
            } catch (final OpenSearchClientException e) {
                try (var r = e.response()) {
                    throw new DatabaseException("Could not execute: %s".formatted(r.getBody().map(Body::bodyAsString).orElse("")), e);
                } catch (IOException ex) {
                    throw new DatabaseException("Could not execute", e);
                }
            }
        } else {
            throw new DatabaseException("liquibase-opensearch extension cannot execute changeset \n" +
                    "Unknown type: " + sql.getClass().getName() +
                    "\nPlease check the following common causes:\n" +
                    "- Verify change set definitions for common error such as: changeType name, changeSet attributes spelling " +
                    "(such as runWith,  context, etc.), and punctuation.\n" +
                    "- Verify that changesets have all the required changeset attributes and do not have invalid attributes for the designated change type.\n" +
                    "- Double-check to make sure your basic setup includes all needed extensions in your Java classpath");
        }
    }

    @Override
    public int update(final SqlStatement sql) throws DatabaseException {
        return update(sql, emptyList());
    }

    @Override
    public int update(final SqlStatement sql, final List<SqlVisitor> sqlVisitors) throws DatabaseException {
        throw new UnsupportedOperationException("no update supported, use execute instead");
    }

    @Override
    public void comment(final String message) {
        log.info(message);
    }

    @Override
    public boolean updatesDatabase() {
        return true;
    }

}
