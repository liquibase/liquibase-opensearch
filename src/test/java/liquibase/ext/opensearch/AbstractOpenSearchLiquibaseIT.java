package liquibase.ext.opensearch;

import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.DatabaseFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Testcontainers
public abstract class AbstractOpenSearchLiquibaseIT {
    protected OpenSearchLiquibaseDatabase database;
    protected OpenSearchConnection connection;

    protected static final String OPENSEARCH_DOCKER_IMAGE_NAME = "opensearchproject/opensearch:2.19.2";

    @Container
    protected OpensearchContainer<?> container = newContainer();

    /**
     * This allows tests to define alternative containers, e.g. enabling security.
     * @return the testcontainer to be used for this test.
     */
    protected OpensearchContainer<?> newContainer() {
        return new OpensearchContainer<>(DockerImageName.parse(OPENSEARCH_DOCKER_IMAGE_NAME));
    }

    @SneakyThrows
    @BeforeEach
    protected void beforeEach() {
        final String url = "opensearch:" + container.getHttpHostAddress();
        final String username = container.getUsername();
        final String password = container.getPassword();
        database = (OpenSearchLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, username, password, null, null);
        connection = (OpenSearchConnection) this.database.getConnection();
    }

    protected OpenSearchClient getOpenSearchClient() {
        return this.connection.getOpenSearchClient();
    }

    protected void doLiquibaseUpdate(final String changeLogFile, final String contexts) throws Exception {
        new CommandScope(UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, this.database)
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, changeLogFile)
                .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, contexts)
                .execute();
    }

    protected void doLiquibaseUpdate(final String changeLogFile) throws Exception {
        this.doLiquibaseUpdate(changeLogFile, "");
    }

    protected boolean indexExists(final String indexName) throws Exception {
        final var request = new ExistsRequest.Builder()
                .index(indexName)
                .build();
        return this.getOpenSearchClient().indices().exists(request).value();
    }

    protected long getDocumentCount(final String indexName, final Query query) throws Exception {
        this.getOpenSearchClient().indices().refresh(r -> r.index(indexName));
        final var request = new CountRequest.Builder()
                .index(indexName)
                .query(query)
                .build();
        return this.getOpenSearchClient().count(request).count();
    }

    protected long getDocumentCount(final String indexName) throws Exception {
        return this.getDocumentCount(indexName, null);
    }

}
