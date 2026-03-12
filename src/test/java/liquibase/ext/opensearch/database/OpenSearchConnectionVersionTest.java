package liquibase.ext.opensearch.database;

import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchConnectionVersionTest {

    @Test
    void keepsReportedVersionWhenNotCompatibilityMode() {
        assertThat(OpenSearchConnection.toEffectiveProductVersion("2.17.1", "1.0.0"))
                .isEqualTo("2.17.1");
    }

    @Test
    void derivesOpenSearch2VersionFromCompatibilityModeResponse() {
        assertThat(OpenSearchConnection.toEffectiveProductVersion("7.10.2", "1.0.0"))
                .isEqualTo("2.0.0");
    }

    @Test
    void derivesOpenSearch3VersionFromCompatibilityModeResponse() {
        assertThat(OpenSearchConnection.toEffectiveProductVersion("7.10.2", "2.0.0"))
                .isEqualTo("3.0.0");
    }

    @Test
    void fallsBackToReportedVersionWhenMinimumIndexCompatibilityIsMissing() {
        assertThat(OpenSearchConnection.toEffectiveProductVersion("7.10.2", null))
                .isEqualTo("7.10.2");
    }

    @Test
    void parsesMajorAndMinorVersionParts() throws DatabaseException {
        assertThat(OpenSearchConnection.parseVersionPart("3.0.0", 0)).isEqualTo(3);
        assertThat(OpenSearchConnection.parseVersionPart("3.0.0", 1)).isEqualTo(0);
    }

    @Test
    void failsOnMalformedVersionPart() {
        assertThatThrownBy(() -> OpenSearchConnection.parseVersionPart("3", 1))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("Invalid OpenSearch version format");
    }
}
