package liquibase.ext.opensearch;

class MultipleOpenSearchNodesLiquibase2IT extends MultipleOpenSearchNodesLiquibaseIT {
    @Override
    protected String openSearchImageName() {
        return OPENSEARCH_2_DOCKER_IMAGE_NAME;
    }
}
