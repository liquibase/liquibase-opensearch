package liquibase.ext.opensearch.integration.spring;

import liquibase.database.ConnectionServiceFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@EnableConfigurationProperties(SpringLiquibaseOpenSearchProperties.class)
@ConditionalOnProperty(prefix = "opensearch", name = "liquibase.enabled", matchIfMissing = true)
@ConditionalOnClass(OpenSearchClient.class)
public class LiquibaseOpenSearchAutoConfiguration {

    @Configuration
    @ConditionalOnBean(OpenSearchClient.class)
    static class WithExistingConnection {
        @Bean
        public SpringLiquibaseOpenSearch getLiquibaseOpenSearch(final OpenSearchClient openSearchClient) {
            final var connection = new OpenSearchConnection(openSearchClient);
            ConnectionServiceFactory.getInstance().register(connection);
            // nothing special to do here - we just had to construct a connection and add it
            return new SpringLiquibaseOpenSearch();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "opensearch", name = "uris")
    @ConditionalOnMissingBean(OpenSearchClient.class)
    static class WithNewConnection {
        @Bean
        public SpringLiquibaseOpenSearch getLiquibaseOpenSearch() {
            return new SpringLiquibaseOpenSearch();
        }
    }

}
