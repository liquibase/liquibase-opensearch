package liquibase.ext.opensearch;

class CustomOpenSearchClientLiquibase2IT extends CustomOpenSearchClientLiquibaseIT {
    @Override
    protected boolean useOpenSearchV2() {
        return true;
    }
}
