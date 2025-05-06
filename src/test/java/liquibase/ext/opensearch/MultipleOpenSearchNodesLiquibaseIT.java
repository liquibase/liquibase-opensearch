package liquibase.ext.opensearch;

import liquibase.database.DatabaseFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultipleOpenSearchNodesLiquibaseIT extends AbstractOpenSearchLiquibaseIT {
    @SneakyThrows
    @BeforeEach
    @Override
    protected void beforeEach() {
        // if we launch two testcontainers they can't see each other and thus don't form a cluster => just use the same URL twice to show that it's being accepted
        // note: don't use the constants here to detect if we ever change them (to make sure that we actively decide on doing a breaking change rather than making it by mistake).
        final String url = "opensearch:" + this.container.getHttpHostAddress() + "," + this.container.getHttpHostAddress();
        final String username = this.container.getUsername();
        final String password = this.container.getPassword();
        this.database = (OpenSearchLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, username, password, null, null);
        this.connection = (OpenSearchConnection) this.database.getConnection();
    }

    @SneakyThrows
    @Test
    void itCreatesTheChangelogAndLockIndices() {
        this.doLiquibaseUpdate("liquibase/ext/changelog.empty.yaml");
        assertThat(this.indexExists(this.database.getDatabaseChangeLogLockTableName())).isTrue();
        assertThat(this.indexExists(this.database.getDatabaseChangeLogTableName())).isTrue();
    }

}
