package liquibase.ext.opensearch.integration.spring;

import liquibase.Scope;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.command.core.helpers.ShowSummaryArgument;
import liquibase.database.DatabaseFactory;
import liquibase.ext.opensearch.database.OpenSearchConnection;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.ui.UIServiceEnum;
import lombok.Getter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;

import static liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase.OPENSEARCH_PREFIX;
import static liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase.OPENSEARCH_URI_SEPARATOR;

public class SpringLiquibaseOpenSearch implements InitializingBean {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private SpringLiquibaseOpenSearchProperties properties;

    @Getter
    protected UIServiceEnum uiService = UIServiceEnum.LOGGER;

    @Override
    public void afterPropertiesSet() throws Exception {
        // liquibase requires the prefix to identify this as an OpenSearch database.
        final var url = OPENSEARCH_PREFIX + String.join(OPENSEARCH_URI_SEPARATOR, properties.uris());

        Scope.child(Scope.Attr.ui.name(), this.uiService.getUiServiceClass().getDeclaredConstructor().newInstance(),
                () -> {
                    final var database = (OpenSearchLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, properties.username(), properties.password(), null, new SpringResourceAccessor(this.resourceLoader));
                    final var connection = (OpenSearchConnection) database.getConnection();
                    new CommandScope(UpdateCommandStep.COMMAND_NAME)
                            .addArgumentValue(ShowSummaryArgument.SHOW_SUMMARY_OUTPUT, UpdateSummaryOutputEnum.LOG)
                            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, properties.liquibase().changelogFile())
                            .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, properties.liquibase().contexts())
                            .addArgumentValue(UpdateCommandStep.LABEL_FILTER_ARG, properties.liquibase().labelFilterArgs())
                            .execute();
                    connection.close();
                    database.close();
                });
    }

}
