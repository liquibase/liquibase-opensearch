package liquibase.ext.opensearch;

class OpenSearchLiquibase2IT extends OpenSearchLiquibaseIT {
    @Override
    protected String openSearchImageName() {
        return OPENSEARCH_2_DOCKER_IMAGE_NAME;
    }
}
