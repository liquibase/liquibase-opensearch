package liquibase.ext.opensearch.integration.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers
@SpringBootTest(properties = {
    "spring.liquibase.enabled=false",
    "opensearch.liquibase.enabled=false"
})
class SpringLiquibaseOpenSearchDisabledIT {
    @Autowired private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBeansOfType(SpringLiquibaseOpenSearch.class).keySet(), empty());
    }

}
