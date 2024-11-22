package liquibase.ext.opensearch.integration.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(SpringLiquibaseOpenSearchProperties.class)
@ConditionalOnProperty({"opensearch.uris", "opensearch.liquibase.enabled"})
public class LiquibaseOpenSearchAutoConfiguration {
    @Bean
    public SpringLiquibaseOpenSearch getLiquibaseOpenSearch() {
        return new SpringLiquibaseOpenSearch();
    }
}
