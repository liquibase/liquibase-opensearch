package liquibase.ext.opensearch.integration.spring;

import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static liquibase.ext.opensearch.AbstractOpenSearchLiquibaseIT.OPENSEARCH_DOCKER_IMAGE_NAME;

@Testcontainers
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.platform=h2",
    "spring.liquibase.change-log=classpath:/liquibase/db/liquibase-changeLog.xml"
})
class SpringLiquibaseOpenSearchDataSourceIT {

    @Container
    protected static OpensearchContainer<?> container = new OpensearchContainer<>(DockerImageName.parse(OPENSEARCH_DOCKER_IMAGE_NAME));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("opensearch.uris", container::getHttpHostAddress);
        registry.add("opensearch.username", container::getUsername);
        registry.add("opensearch.password", container::getPassword);
    }

    /**
     * On context load liquibase is automatically being triggered (hard to test as we'd have to construct our own OpenSearch client).
     */
    @Test
    void contextLoads() {
    }

}
