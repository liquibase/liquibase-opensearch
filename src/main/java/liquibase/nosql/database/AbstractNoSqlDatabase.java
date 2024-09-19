package liquibase.nosql.database;

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

import liquibase.CatalogAndSchema;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.ValidationErrors;
import liquibase.statement.DatabaseFunction;
import liquibase.structure.DatabaseObject;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * {@link AbstractNoSqlDatabase} is extended by all supported NoSql databases as a facade to the underlying database.
 * The physical connection can be retrieved from the {@link AbstractNoSqlDatabase} implementation, as well as any
 * database-specific characteristics.
 */
@NoArgsConstructor
public abstract class AbstractNoSqlDatabase extends AbstractJdbcDatabase implements Database {

    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return false;
    }

    @Override
    public boolean supportsSequences() {
        return false;
    }

    @Override
    public boolean supportsDropTableCascadeConstraints() {
        return false;
    }

    @Override
    public boolean supportsAutoIncrement() {
        return false;
    }

    @Override
    public String getLineComment() {
        return "";
    }

    @Override
    public String getAutoIncrementClause(final BigInteger startWith, final BigInteger incrementBy, final String generationType, final Boolean defaultOnNull) {
        return null;
    }

    @Override
    public boolean isSystemObject(final DatabaseObject example) {
        return false;
    }

    @Override
    public boolean isLiquibaseObject(final DatabaseObject object) {
        return false;
    }

    @Override
    public String getViewDefinition(final CatalogAndSchema schema, final String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsTablespaces() {
        return false;
    }

    @Override
    public boolean supportsCatalogs() {
        return false;
    }

    @Override
    public CatalogAndSchema.CatalogAndSchemaCase getSchemaAndCatalogCase() {
        return CatalogAndSchema.CatalogAndSchemaCase.ORIGINAL_CASE;
    }

    @Override
    public boolean supportsSchemas() {
        return false;
    }

    @Override
    public boolean supportsCatalogInObjectName(final Class<? extends DatabaseObject> type) {
        return false;
    }

    @Override
    public String generatePrimaryKeyName(final String tableName) {
        return null;
    }

    @Override
    public abstract void dropDatabaseObjects(final CatalogAndSchema schemaToDrop) throws LiquibaseException;

    @Override
    public boolean supportsRestrictForeignKeys() {
        return false;
    }

    @Override
    public List<DatabaseFunction> getDateFunctions() {
        // irrelevant (will never be called as this is not SQL being processed)
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsForeignKeyDisable() {
        return false;
    }

    @Override
    public boolean disableForeignKeyChecks() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableForeignKeyChecks() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }

    @Override
    public boolean isReservedWord(final String string) {
        return false;
    }

    @Override
    public boolean isFunction(String string) {
        return false;
    }

    @Override
    public int getDataTypeMaxParameters(String dataTypeName) {
        return 0;
    }

    @Override
    public boolean dataTypeIsNotModifiable(String typeName) {
        return false;
    }

    @Override
    public String generateDatabaseFunctionValue(DatabaseFunction databaseFunction) {
        return null;
    }

    @Override
    public boolean createsIndexesForForeignKeys() {
        //Not applicable
        return false;
    }

    @Override
    public boolean supportsPrimaryKeyNames() {
        //Not applicable
        return false;
    }

    @Override
    public boolean supportsNotNullConstraintNames() {
        //Not applicable
        return false;
    }

    @Override
    public boolean supportsBatchUpdates() {
        return false;
    }

    @Override
    public boolean requiresExplicitNullForColumns() {
        //Not applicable
        return false;
    }

    @Override
    public String getSystemSchema() {
        return null;
    }

    @Override
    public ValidationErrors validate() {
        return null;
    }

    @Override
    public abstract String getDefaultDriver(final String url);

    @Override
    public boolean requiresUsername() {
        return false;
    }

    @Override
    public boolean requiresPassword() {
        return false;
    }

    @Override
    public boolean getAutoCommitMode() {
        return false;
    }

    @Override
    public boolean supportsDDLInTransaction() {
        return false;
    }

    @Override
    public abstract String getDatabaseProductName();

    @Override
    public boolean isCorrectDatabaseImplementation(final DatabaseConnection conn) throws DatabaseException {
        return getDatabaseProductName().equals(conn.getDatabaseProductName());
    }

    @Override
    public String toString() {
        return getDatabaseProductName() + " : "
                + ofNullable(getConnection()).map(DatabaseConnection::getURL).orElse("NOT CONNECTED");
    }

}
