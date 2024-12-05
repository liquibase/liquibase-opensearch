package liquibase.ext.opensearch.integration.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * @param uris URL of the OpenSearch instances to use. If multiple URLs are listed they must all belong to the same cluster.
 * @param username Username for authentication with OpenSearch.
 * @param password Password for authentication with OpenSearch.
 * @param liquibase Liquibase-specific properties.
 */
@ConfigurationProperties("opensearch")
public record SpringLiquibaseOpenSearchProperties(
    @DefaultValue("http://localhost:9200")
    List<String> uris,
    String username,
    String password,

    @DefaultValue()
    SpringLiquibaseProperties liquibase
    ) {
    /**
     * @param changelogFile Path to the liquibase changelog file (must be on the classpath).
     * @param contexts Context filter to be used.
     * @param labelFilterArgs Label filter to be used.
     */
    public record SpringLiquibaseProperties(
            @DefaultValue("true")
            Boolean enabled,

            @DefaultValue("db.changelog/db.changelog-master.yaml")
            String changelogFile,

            String contexts,

            String labelFilterArgs
    ) {}
}
