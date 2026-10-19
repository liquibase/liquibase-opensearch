package liquibase.ext.opensearch.database;

import liquibase.CatalogAndSchema;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.nosql.database.AbstractNoSqlDatabase;
import lombok.NoArgsConstructor;

import java.util.ResourceBundle;

@NoArgsConstructor
public class OpenSearchLiquibaseDatabase extends AbstractNoSqlDatabase {
    public static final String PRODUCT_NAME = "OpenSearch";
    public static final String PRODUCT_SHORT_NAME = "opensearch";
    public static final String OPENSEARCH_PREFIX = PRODUCT_SHORT_NAME + ":";
    public static final String OPENSEARCH_URI_SEPARATOR = ",";

    /**
     * OpenSearch 1.x is end of life and not supported.
     */
    static final int MINIMUM_MAJOR_VERSION = 2;
    static final int MINIMUM_MINOR_VERSION = 0;

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("liquibase/i18n/liquibase-opensearch");

    public OpenSearchLiquibaseDatabase(final OpenSearchConnection openSearchConnection) {
        super();
        this.setConnection(openSearchConnection);
    }

    @Override
    public void setConnection(final DatabaseConnection conn) {
        super.setConnection(conn);
        if (conn != null) {
            this.ensureSupportedVersion(conn);
        }
    }

    private void ensureSupportedVersion(final DatabaseConnection conn) {
        final int major;
        final int minor;
        try {
            major = conn.getDatabaseMajorVersion();
            minor = conn.getDatabaseMinorVersion();
        } catch (final DatabaseException e) {
            throw new UnexpectedLiquibaseException("Could not determine the OpenSearch version", e);
        }
        if (major < MINIMUM_MAJOR_VERSION || (major == MINIMUM_MAJOR_VERSION && minor < MINIMUM_MINOR_VERSION)) {
            throw new UnexpectedLiquibaseException(String.format(
                    "OpenSearch %d.%d or newer is required, but the cluster runs %d.%d",
                    MINIMUM_MAJOR_VERSION, MINIMUM_MINOR_VERSION, major, minor));
        }
    }

    @Override
    public void dropDatabaseObjects(final CatalogAndSchema schemaToDrop) throws LiquibaseException {
        // dropping "all database objects" would mean deleting every index of the cluster, which is not supported
        throw new DatabaseException(String.format(RESOURCE_BUNDLE.getString("command.unsupported"), "drop-all"));
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
