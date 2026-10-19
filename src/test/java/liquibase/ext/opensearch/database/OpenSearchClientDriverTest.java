package liquibase.ext.opensearch.database;

import org.junit.jupiter.api.Test;

import java.sql.SQLFeatureNotSupportedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchClientDriverTest {

    @Test
    void getParentLoggerIsNotSupported() {
        final var driver = new OpenSearchClientDriver();
        assertThatThrownBy(driver::getParentLogger).isInstanceOf(SQLFeatureNotSupportedException.class);
    }
}
