package liquibase.ext.opensearch.changelog;

import liquibase.ContextExpression;
import liquibase.change.CheckSum;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.RanChangeSet;
import liquibase.exception.DatabaseException;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchHistoryServiceTest {

    /**
     * {@link OpenSearchHistoryService#adjustRepository()} creates an OpenSearch index which contains the same fields as
     * {@link RanChangeSet}. this test ensures that we cover all fields - every time a field is added or removed from the
     * class this test will fail. if this happens you must adapt both {@link OpenSearchHistoryService#adjustRepository()}
     * as well as this test.
     */
    @Test
    void ensureThatAllRanChangeSetFieldsAreCovered() {
        final var allFields = Arrays.stream(RanChangeSet.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName);
        assertThat(allFields).containsExactlyInAnyOrder(
                "id",
                "changeLog",
                "storedChangeLog",
                "author",
                "lastCheckSum",
                "dateExecuted",
                "tag",
                "execType",
                "description",
                "comments",
                "orderExecuted",
                "contextExpression",
                "labels",
                "deploymentId",
                "liquibaseVersion"
        );
    }

    /**
     * {@link OpenSearchHistoryService#adjustRepository()} creates an OpenSearch index which contains the same fields as
     * {@link CheckSum}. this test ensures that we cover all fields - every time a field is added or removed from the
     * class this test will fail. if this happens you must adapt both {@link OpenSearchHistoryService#adjustRepository()}
     * as well as this test.
     */
    @Test
    void ensureThatAllCheckSumFieldsAreCovered() {
        final var allFields = Arrays.stream(CheckSum.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName);
        assertThat(allFields).containsExactlyInAnyOrder(
                "version",
                "storedCheckSum"
        );
    }

    /**
     * {@link OpenSearchHistoryService#adjustRepository()} creates an OpenSearch index which contains the same fields as
     * {@link ContextExpression}. this test ensures that we cover all fields - every time a field is added or removed from the
     * class this test will fail. if this happens you must adapt both {@link OpenSearchHistoryService#adjustRepository()}
     * as well as this test.
     */
    @Test
    void ensureThatAllContextExpressionFieldsAreCovered() {
        final var allFields = Arrays.stream(ContextExpression.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName);
        assertThat(allFields).containsExactlyInAnyOrder(
                "contexts",
                "originalString"
        );
    }

    /**
     * {@link OpenSearchHistoryService#getNextSequenceValue()} caches the counter in memory, so
     * {@link OpenSearchHistoryService#reset()} has to drop it. Otherwise a rollback followed by an
     * update keeps counting from the stale value instead of restarting from the changelog index.
     */
    @Test
    void resetDropsTheCachedSequenceValue() throws DatabaseException {
        // Without a connection the sequence starts from zero, so the values are predictable
        // and the test does not need a running OpenSearch instance.
        final var historyService = new OpenSearchHistoryService();
        historyService.setDatabase(new OpenSearchLiquibaseDatabase());

        assertThat(historyService.getNextSequenceValue()).isEqualTo(1);
        assertThat(historyService.getNextSequenceValue()).isEqualTo(2);

        historyService.reset();

        assertThat(historyService.getNextSequenceValue()).isEqualTo(1);
    }

    /**
     * The document ID used to update/delete an entry must match the one it has been written with (i.e.
     * {@link RanChangeSet#toString()}), also for file paths which liquibase normalises.
     */
    @ParameterizedTest
    @ValueSource(strings = {"db/changelog.yaml", "/db/changelog.yaml", "db/./changelog.yaml", "classpath:db/changelog.yaml", "db\\changelog.yaml"})
    void theDocumentIdMatchesTheStoredEntry(final String filePath) {
        final var changeSet = new ChangeSet("1", "author", false, false, filePath, null, null, null);
        final var storedEntry = new RanChangeSet(changeSet, ChangeSet.ExecType.EXECUTED, null, null);

        assertThat(OpenSearchHistoryService.documentId(changeSet))
                .isEqualTo(storedEntry.toString())
                .isEqualTo("db/changelog.yaml::1::author");
    }
}
