package liquibase.ext.opensearch.statement;

import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.logging.Logger;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.opensearch.client.opensearch.generic.Bodies;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient.ClientOptions;
import org.opensearch.client.opensearch.generic.Requests;

import java.io.IOException;
import java.util.Optional;

@AllArgsConstructor
@Getter
@EqualsAndHashCode(callSuper = true)
public class HttpRequestStatement extends AbstractOpenSearchStatement implements OpenSearchExecuteStatement {

    private final Logger log = Scope.getCurrentScope().getLog(getClass());

    private String method;
    private String path;
    private String body;

    @Override
    public String toString() {
        return String.format("HTTP %s request against %s (with a body of size %d)",
                this.getMethod(),
                this.getPath(),
                Optional.ofNullable(this.getBody()).map(String::length).orElse(0));
    }

    @Override
    public void execute(final OpenSearchLiquibaseDatabase database) throws DatabaseException {
        log.info(this.toString());

        final var httpClient = this.getOpenSearchClient(database).generic().withClientOptions(ClientOptions.throwOnHttpErrors());

        final var request = Requests.builder()
                .endpoint(this.getPath())
                .method(this.getMethod())
                .body(Bodies.json(this.getBody()))
                .build();

        try (final var response = httpClient.execute(request)) {
            if (response.getStatus() >= 400) {
                throw new DatabaseException(String.format("HTTP request failed with code %d: %s", response.getStatus(), response));
            }
        } catch (final IOException e) {
            throw new DatabaseException("failed to execute the HTTP request", e);
        }
    }

}
