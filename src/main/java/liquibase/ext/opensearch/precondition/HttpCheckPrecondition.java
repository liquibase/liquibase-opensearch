package liquibase.ext.opensearch.precondition;

import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.changelog.visitor.ChangeExecListener;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.exception.PreconditionErrorException;
import liquibase.exception.PreconditionFailedException;
import liquibase.exception.ValidationErrors;
import liquibase.exception.Warnings;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.precondition.AbstractPrecondition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.generic.Bodies;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.transport.TransportOptions;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Executes an HTTP request against OpenSearch and checks the response: the precondition holds if the response has the
 * expected status code and (if {@code jsonPointer} is set) the value at that JSON pointer of the response body is the
 * expected value.
 */
@NoArgsConstructor
@Getter
@Setter
public class HttpCheckPrecondition extends AbstractPrecondition {

    public static final String NAME = "httpCheck";
    public static final String NAMESPACE = "http://www.liquibase.org/xml/ns/opensearch";

    /**
     * Same as the {@code httpCheckMethods} of the XSD: methods which can be used to read data from OpenSearch.
     * {@code POST} is needed for some read APIs (e.g. {@code _search} and {@code _count}).
     */
    private static final List<String> ALLOWED_METHODS = List.of("GET", "HEAD", "POST");
    private static final int DEFAULT_EXPECTED_STATUS_CODE = 200;

    private String method = "GET";
    private String contentType;
    private String path;
    private String body;
    private Integer expectedStatusCode = DEFAULT_EXPECTED_STATUS_CODE;
    private String jsonPointer;
    private String expectedValue;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getSerializedObjectNamespace() {
        return NAMESPACE;
    }

    @Override
    public Warnings warn(final Database database) {
        return new Warnings();
    }

    @Override
    public ValidationErrors validate(final Database database) {
        final var errors = new ValidationErrors();
        if (StringUtils.isBlank(this.method)) {
            errors.addError("method is required for " + NAME);
        } else if (!ALLOWED_METHODS.contains(this.method)) {
            errors.addError("method must be one of %s but was '%s'".formatted(String.join(", ", ALLOWED_METHODS), this.method));
        }
        if (StringUtils.isBlank(this.path)) {
            errors.addError("path is required for " + NAME);
        } else if (!this.path.startsWith("/")) {
            errors.addError("path must start with '/' but was '%s'".formatted(this.path));
        }
        if (this.expectedStatusCode == null) {
            errors.addError("expectedStatusCode is required for " + NAME);
        } else if (this.expectedStatusCode < 100 || this.expectedStatusCode > 599) {
            errors.addError("expectedStatusCode must be between 100 and 599 but was %d".formatted(this.expectedStatusCode));
        }
        if (this.jsonPointer != null) {
            if (!this.jsonPointer.startsWith("/")) {
                errors.addError("jsonPointer must start with '/' but was '%s'".formatted(this.jsonPointer));
            }
            if (this.expectedValue == null) {
                errors.addError("expectedValue is required if jsonPointer is set");
            }
            if ("HEAD".equals(this.method)) {
                errors.addError("jsonPointer can't be used with HEAD requests as their response has no body");
            }
        } else if (this.expectedValue != null) {
            errors.addError("jsonPointer is required if expectedValue is set");
        }
        return errors;
    }

    @Override
    public void check(final Database database, final DatabaseChangeLog changeLog, final ChangeSet changeSet,
                      final ChangeExecListener changeExecListener) throws PreconditionFailedException, PreconditionErrorException {
        // liquibase only calls `validate` for the preconditions of a changelog, not for the ones of a changeset
        final var errors = this.validate(database);
        if (errors.hasErrors()) {
            throw new PreconditionErrorException(new DatabaseException("invalid %s: %s"
                    .formatted(NAME, String.join(", ", errors.getErrorMessages()))), changeLog, this);
        }

        final var transportOptionsBuilder = TransportOptions.builder();
        if (this.contentType != null) {
            transportOptionsBuilder.addHeader("Content-Type", this.contentType);
        }
        // note: no `throwOnHttpErrors`: an error status code (e.g. 404) is a valid result which is checked below
        final var httpClient = this.getOpenSearchConnection(database, changeLog).getOpenSearchClient().generic()
                .withTransportOptions(transportOptionsBuilder.build());

        final var requestBuilder = Requests.builder()
                .endpoint(this.path)
                .method(this.method);
        if (this.body != null) {
            requestBuilder.body(Bodies.json(this.body));
        }

        try (final var response = httpClient.execute(requestBuilder.build())) {
            if (response.getStatus() != this.expectedStatusCode) {
                if (isErrorStatus(response.getStatus())) {
                    // e.g. with `expectedStatusCode: 404` and `onFail: MARK_RAN` an outage must not mark the changeset as ran
                    throw new PreconditionErrorException(new DatabaseException("%s: expected HTTP status %d but got %d"
                            .formatted(this, this.expectedStatusCode, response.getStatus())), changeLog, this);
                }
                throw new PreconditionFailedException("%s: expected HTTP status %d but got %d"
                        .formatted(this, this.expectedStatusCode, response.getStatus()), changeLog, this);
            }
            if (this.jsonPointer != null) {
                final var responseBody = response.getBody().map(Body::bodyAsString)
                        .orElseThrow(() -> new DatabaseException("%s: the response has no body".formatted(this)));
                this.checkJsonPointer(JsonMapper.shared().readTree(responseBody), changeLog);
            }
        } catch (final IOException | DatabaseException | JacksonException e) {
            throw new PreconditionErrorException(e, changeLog, this);
        }
    }

    /**
     * @return {@code true} if the status code means that the state could not be checked (server error or missing
     * permissions) rather than that the state differs from the expected one.
     */
    private static boolean isErrorStatus(final int statusCode) {
        return statusCode >= 500 || statusCode == 401 || statusCode == 403;
    }

    private void checkJsonPointer(final JsonNode responseBody, final DatabaseChangeLog changeLog) throws PreconditionFailedException {
        final var node = responseBody.at(this.jsonPointer);
        if (node.isMissingNode()) {
            throw new PreconditionFailedException("%s: '%s' not found in the response"
                    .formatted(this, this.jsonPointer), changeLog, this);
        }
        // strings are compared without their quotes, everything else (numbers, booleans, null, objects, arrays) as JSON
        final var actualValue = node.isString() ? node.stringValue() : node.toString();
        if (!actualValue.equals(this.expectedValue)) {
            throw new PreconditionFailedException("%s: expected '%s' at '%s' but got '%s'"
                    .formatted(this, this.expectedValue, this.jsonPointer, actualValue), changeLog, this);
        }
    }

    private OpenSearchConnection getOpenSearchConnection(final Database database, final DatabaseChangeLog changeLog) throws PreconditionErrorException {
        final var connection = Optional.ofNullable(database).map(Database::getConnection).orElse(null);
        if (connection instanceof OpenSearchConnection openSearchConnection) {
            return openSearchConnection;
        }
        throw new PreconditionErrorException(new DatabaseException("%s is only supported for OpenSearch".formatted(NAME)), changeLog, this);
    }

    @Override
    public String toString() {
        return "%s %s %s".formatted(NAME, this.method, this.path);
    }
}
