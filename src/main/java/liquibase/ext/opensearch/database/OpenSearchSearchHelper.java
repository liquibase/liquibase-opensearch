package liquibase.ext.opensearch.database;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helpers around the OpenSearch search API.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenSearchSearchHelper {

    static final int PAGE_SIZE = 1000;
    private static final String SCROLL_KEEP_ALIVE = "1m";

    /**
     * Loads <em>all</em> documents of an index. A plain search is limited to {@code index.max_result_window} entries
     * (10 by default), thus the scroll API is used to page through the whole index.
     * <p>
     * Note: PIT + {@code search_after} would be the preferred approach according to the OpenSearch documentation but
     * it requires OpenSearch 2.4+, whereas scrolling works with all supported versions.
     *
     * @param sortFields fields to sort the result by (ascending, missing values first); may be empty
     * @return a mutable list of all documents in the index
     */
    public static <T> List<T> searchAll(final OpenSearchClient client, final String index, final Class<T> clazz, final String... sortFields) throws IOException {
        final var sort = Arrays.stream(sortFields)
                .map(field -> SortOptions.of(so -> so.field(f -> f.field(field).order(SortOrder.Asc).missing(FieldValue.of("_first")))))
                .toList();
        final var results = new ArrayList<T>();
        SearchResponse<T> response = client.search(s -> s
                        .index(index)
                        .size(PAGE_SIZE)
                        .sort(sort)
                        .scroll(t -> t.time(SCROLL_KEEP_ALIVE)),
                clazz);
        // the scroll ID may change between responses, always use the latest one
        var scrollId = response.scrollId();
        try {
            while (true) {
                final var hits = response.hits().hits();
                hits.stream().map(Hit::source).forEach(results::add);
                if (hits.size() < PAGE_SIZE) {
                    return results;
                }
                final var currentScrollId = scrollId;
                response = client.scroll(s -> s
                                .scrollId(currentScrollId)
                                .scroll(t -> t.time(SCROLL_KEEP_ALIVE)),
                        clazz);
                scrollId = response.scrollId();
            }
        } finally {
            final var lastScrollId = scrollId;
            if (lastScrollId != null) {
                client.clearScroll(c -> c.scrollId(lastScrollId));
            }
        }
    }
}
