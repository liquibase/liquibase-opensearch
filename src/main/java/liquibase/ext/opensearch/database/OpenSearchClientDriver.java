package liquibase.ext.opensearch.database;

import liquibase.Scope;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import static liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase.OPENSEARCH_PREFIX;

public class OpenSearchClientDriver implements Driver {
    @Override
    public Connection connect(final String url, final Properties info) {
        //Not applicable for non JDBC DBs
        throw new UnsupportedOperationException("Cannot initiate a SQL Connection for a NoSql DB");
    }

    public static boolean isOpenSearchURL(final String url) {
        return StringUtils.trimToEmpty(url).startsWith(OPENSEARCH_PREFIX);
    }

    @Override
    public boolean acceptsURL(final String url) {
        return isOpenSearchURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return (Logger) Scope.getCurrentScope().getLog(getClass());
    }
}
