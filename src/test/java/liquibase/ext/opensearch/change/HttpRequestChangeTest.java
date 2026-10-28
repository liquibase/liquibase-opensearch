package liquibase.ext.opensearch.change;

import liquibase.exception.ValidationErrors;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void itAcceptsMethodsWhichCanModifyData(final String method) {
        assertThat(validate(change(method, "/testindex", null)).getErrorMessages()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "FOO", "put"})
    void itRejectsOtherMethods(final String method) {
        assertThat(validate(change(method, "/testindex", null)).getErrorMessages())
                .containsExactly("method must be one of POST, PUT, DELETE, PATCH but was '%s'".formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"testindex", " /testindex"})
    void itRejectsPathsWithoutLeadingSlash(final String path) {
        assertThat(validate(change("PUT", path, null)).getErrorMessages())
                .containsExactly("path must start with '/' but was '%s'".formatted(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankValuesAreReportedAsEmpty(final String value) {
        assertThat(validate(change(value, value, null)).getErrorMessages()).containsExactlyInAnyOrder(
                "method is empty for httpRequest on opensearch",
                "path is empty for httpRequest on opensearch");
    }

    @Test
    void itReportsAllErrorsTogether() {
        assertThat(validate(change("GET", "testindex", null)).getErrorMessages()).hasSize(2);
    }
}
