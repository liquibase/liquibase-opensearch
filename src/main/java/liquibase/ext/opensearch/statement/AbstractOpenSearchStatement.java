package liquibase.ext.opensearch.statement;

import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.statement.AbstractSqlStatement;
import org.opensearch.client.opensearch.OpenSearchClient;

public abstract class AbstractOpenSearchStatement extends AbstractSqlStatement {

    @Override
    public boolean continueOnError() {
        return false;
    }

    @Override
    public boolean skipOnUnsupported() {
        return false;
    }

    @Override
    public abstract String toString();

    protected OpenSearchClient getOpenSearchClient(final OpenSearchLiquibaseDatabase database) {
        final var connection = (OpenSearchConnection)database.getConnection();
        return connection.getOpenSearchClient();
    }

}
