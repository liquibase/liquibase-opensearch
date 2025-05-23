package liquibase.ext.opensearch;

class MultipleOpenSearchNodesLiquibase2IT extends MultipleOpenSearchNodesLiquibaseIT {
    @Override
    protected boolean useOpenSearchV2() {
        return true;
    }
}
