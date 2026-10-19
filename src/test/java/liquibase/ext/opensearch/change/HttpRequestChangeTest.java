package liquibase.ext.opensearch.change;

import liquibase.exception.ValidationErrors;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestChangeTest {

    private static HttpRequestChange change(final String method, final String path, final String body) {
        final var change = new HttpRequestChange();
        change.setMethod(method);
        change.setPath(path);
        change.setBody(body);
        return change;
    }

    private static ValidationErrors validate(final HttpRequestChange change) {
        return change.validate(new OpenSearchLiquibaseDatabase());
    }

    @Test
    void aValidChangeHasNoErrors() {
        assertThat(validate(change("PUT", "/testindex", "{}")).getErrorMessages()).isEmpty();
    }

    @Test
    void theBodyIsOptional() {
        assertThat(validate(change("DELETE", "/testindex", null)).getErrorMessages()).isEmpty();
    }

    @Test
    void theMethodIsRequired() {
        assertThat(validate(change(null, "/testindex", "{}")).getErrorMessages())
                .containsExactly("method is required for httpRequest on opensearch");
    }

    @Test
    void thePathIsRequired() {
        assertThat(validate(change("PUT", null, "{}")).getErrorMessages())
                .containsExactly("path is required for httpRequest on opensearch");
    }
}
