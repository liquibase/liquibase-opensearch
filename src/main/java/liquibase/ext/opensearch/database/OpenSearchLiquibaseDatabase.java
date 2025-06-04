package liquibase.ext.opensearch.database;

import liquibase.CatalogAndSchema;
import liquibase.exception.LiquibaseException;
import liquibase.nosql.database.AbstractNoSqlDatabase;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class OpenSearchLiquibaseDatabase extends AbstractNoSqlDatabase {
    public static final String PRODUCT_NAME = "OpenSearch";
    public static final String PRODUCT_SHORT_NAME = "opensearch";
    public static final String OPENSEARCH_PREFIX = PRODUCT_SHORT_NAME + ":";
    public static final String OPENSEARCH_URI_SEPARATOR = ",";

    public OpenSearchLiquibaseDatabase(final OpenSearchConnection openSearchConnection) {
        super();
        this.setConnection(openSearchConnection);
    }

    @Override
    public void dropDatabaseObjects(final CatalogAndSchema schemaToDrop) throws LiquibaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDefaultDriver(final String url) {
        if (OpenSearchClientDriver.isOpenSearchURL(url)) {
            return OpenSearchClientDriver.class.getName();
        }
        return null;
    }

    @Override
    public String getDatabaseProductName() {
        return PRODUCT_NAME;
    }

    @Override
    public String getShortName() {
        return PRODUCT_SHORT_NAME;
    }

    @Override
    public Integer getDefaultPort() {
        return 9200;
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return PRODUCT_NAME;
    }

    @Override
    public String getDatabaseChangeLogTableName() {
        // OpenSearch only supports lowercase index names
        return super.getDatabaseChangeLogTableName().toLowerCase();
    }

    @Override
    public String getDatabaseChangeLogLockTableName() {
        // OpenSearch only supports lowercase index names
        return super.getDatabaseChangeLogLockTableName().toLowerCase();
    }
}
