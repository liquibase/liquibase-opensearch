package liquibase.ext.opensearch;

class OpenSearchLiquibase2IT extends OpenSearchLiquibaseIT {
    @Override
    protected boolean useOpenSearchV2() {
        return true;
    }
}
