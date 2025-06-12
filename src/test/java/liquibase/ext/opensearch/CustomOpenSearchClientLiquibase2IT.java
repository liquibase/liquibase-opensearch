package liquibase.ext.opensearch;

class CustomOpenSearchClientLiquibase2IT extends CustomOpenSearchClientLiquibaseIT {
    @Override
    protected String openSearchImageName() {
        return OPENSEARCH_2_DOCKER_IMAGE_NAME;
    }
}
