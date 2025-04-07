package liquibase.ext.opensearch.integration.spring;

import org.opensearch.testcontainers.OpensearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static liquibase.ext.opensearch.AbstractOpenSearchLiquibaseIT.OPENSEARCH_DOCKER_IMAGE_NAME;

@Testcontainers
@SpringBootTest
public class SpringLiquibaseOpenSearchExistingConnectionIT {

    @Container
    protected static OpensearchContainer<?> container = new OpensearchContainer<>(DockerImageName.parse(OPENSEARCH_DOCKER_IMAGE_NAME));

    // TODO

}
