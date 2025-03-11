package liquibase.ext.opensearch.integration.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

/**
 * @param enabled         Defines whether the liquibase OpenSearch integration is enabled. Has no impact on other liquibase or OpenSearch integrations.
 * @param changelogFile   Path to the liquibase changelog file (must be on the classpath).
 * @param contexts        Context filter to be used.
 * @param parameters      Change log parameters.
 * @param labelFilterArgs Label filter to be used.
 */
@ConfigurationProperties("opensearch.liquibase")
public record SpringLiquibaseOpenSearchProperties(
        @DefaultValue("true")
        Boolean enabled,

        @DefaultValue("db/changelog/db.changelog-master.yaml")
        String changelogFile,

        String contexts,

        Map<String, String> parameters,

        String labelFilterArgs
) {
}
