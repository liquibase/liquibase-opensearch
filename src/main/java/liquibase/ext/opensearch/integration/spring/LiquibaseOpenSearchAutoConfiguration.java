package liquibase.ext.opensearch.integration.spring;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

@AutoConfiguration
@EnableConfigurationProperties(SpringLiquibaseOpenSearchProperties.class)
@Conditional(LiquibaseOpenSearchAutoConfiguration.LiquibaseOpenSearchCondition.class)
@ConditionalOnClass(OpenSearchClient.class)
public class LiquibaseOpenSearchAutoConfiguration {
    static final class LiquibaseOpenSearchCondition extends AllNestedConditions {
        LiquibaseOpenSearchCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "opensearch", name = "liquibase.enabled", matchIfMissing = true)
        private static final class LiquibaseOpenSearchEnabledCondition {
        }

        @ConditionalOnProperty(prefix = "opensearch", name = "uris")
        private static final class LiquibaseOpenSearchUrlCondition {

        }
    }

    @Bean
    public SpringLiquibaseOpenSearch getLiquibaseOpenSearch() {
        return new SpringLiquibaseOpenSearch();
    }
}
