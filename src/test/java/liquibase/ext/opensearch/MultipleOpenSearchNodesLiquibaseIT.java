package liquibase.ext.opensearch;

import liquibase.database.DatabaseFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MultipleOpenSearchNodesLiquibaseIT extends AbstractOpenSearchLiquibaseIT {
    @SneakyThrows
    @BeforeEach
    protected void beforeEach() {
        // if we launch two testcontainers they can't see each other and thus don't form a cluster => just use the same URL twice to show that it's being accepted
        final String url = "opensearch:" + this.container.getHttpHostAddress() + ";" + this.container.getHttpHostAddress();
        final String username = container.getUsername();
        final String password = container.getPassword();
        database = (OpenSearchLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, username, password, null, null);
        connection = (OpenSearchConnection) this.database.getConnection();
    }

    @SneakyThrows
    @Test
    public void itCreatesTheChangelogAndLockIndices() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        assertThat(this.indexExists(this.database.getDatabaseChangeLogLockTableName())).isTrue();
        assertThat(this.indexExists(this.database.getDatabaseChangeLogTableName())).isTrue();
    }

}
