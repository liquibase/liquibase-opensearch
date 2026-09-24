package liquibase.ext.opensearch.database;

import liquibase.ext.opensearch.AbstractOpenSearchLiquibaseIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;

import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchSearchHelperIT extends AbstractOpenSearchLiquibaseIT {

    private static final String INDEX = "searchall-test";

    @SneakyThrows
    private void indexDocuments(final int count) {
        final var bulk = new BulkRequest.Builder().index(INDEX).refresh(Refresh.True);
        IntStream.range(0, count)
                .forEach(i -> bulk.operations(
                        op -> op.index(idx -> idx.id(String.valueOf(i)).document(Map.of("n", i)))
                ));
        assertThat(this.getOpenSearchClient().bulk(bulk.build()).errors()).isFalse();
    }

    @SneakyThrows
    private long openScrollContexts() {
        return this.getOpenSearchClient().indices().stats().all().total().search().scrollCurrent();
    }

    @SneakyThrows
    @Test
    void itReturnsAllDocumentsAcrossMultiplePages() {
        // more than the default max_result_window (10) and not a multiple of the page size, so the last page is partial
        final var count = OpenSearchSearchHelper.PAGE_SIZE * 2 + 7;
        this.indexDocuments(count);

        final var result = OpenSearchSearchHelper.searchAll(this.getOpenSearchClient(), INDEX, Map.class, "n");

        assertThat(result).hasSize(count);
        assertThat(result).extracting(m -> ((Number) m.get("n")).intValue()).containsExactlyElementsOf(IntStream.range(0, count).boxed().toList());
        assertThat(this.openScrollContexts()).isZero();
    }

    @SneakyThrows
    @Test
    void itReturnsAllDocumentsWhenCountIsAMultipleOfThePageSize() {
        final var count = OpenSearchSearchHelper.PAGE_SIZE;
        this.indexDocuments(count);

        assertThat(OpenSearchSearchHelper.searchAll(this.getOpenSearchClient(), INDEX, Map.class, "n")).hasSize(count);
        assertThat(this.openScrollContexts()).isZero();
    }

    /**
     * Documents without the sort field must be sorted first and must not be lost across page boundaries; without a
     * stable sort the order across pages would be undefined.
     */
    @SneakyThrows
    @Test
    void itReturnsDocumentsWithoutTheSortFieldFirstAndDoesNotSkipTies() {
        final var withoutSortField = OpenSearchSearchHelper.PAGE_SIZE + 5;
        final var withSortField = 5;
        final var bulk = new BulkRequest.Builder().index(INDEX).refresh(Refresh.True);
        IntStream.range(0, withoutSortField)
                .forEach(i -> bulk.operations(op -> op.index(idx -> idx.id("x" + i).document(Map.of("other", "x")))));
        IntStream.range(0, withSortField)
                .forEach(i -> bulk.operations(op -> op.index(idx -> idx.id(String.valueOf(i)).document(Map.of("n", i)))));
        assertThat(this.getOpenSearchClient().bulk(bulk.build()).errors()).isFalse();

        final var result = OpenSearchSearchHelper.searchAll(this.getOpenSearchClient(), INDEX, Map.class, "n");

        assertThat(result).hasSize(withoutSortField + withSortField);
        assertThat(result.subList(0, withoutSortField)).allSatisfy(m -> assertThat(m).doesNotContainKey("n"));
        assertThat(result.subList(withoutSortField, result.size()))
                .extracting(m -> ((Number) m.get("n")).intValue())
                .containsExactlyElementsOf(IntStream.range(0, withSortField).boxed().toList());
        assertThat(this.openScrollContexts()).isZero();
    }

    @SneakyThrows
    @Test
    void itReturnsAMutableListForAnEmptyIndex() {
        this.getOpenSearchClient().indices().create(c -> c.index(INDEX).mappings(m -> m.properties("n", p -> p.integer(i -> i))));

        final var result = OpenSearchSearchHelper.searchAll(this.getOpenSearchClient(), INDEX, Map.class, "n");

        assertThat(result).isEmpty();
        // ensure mutability of returned list
        result.add(Map.of());
        assertThat(result).hasSize(1);
    }
}
