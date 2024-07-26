package liquibase.ext.opensearch.executor;

/*-
 * #%L
 * Liquibase NoSql Extension
 * %%
 * Copyright (C) 2020 Mastercard
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.ext.opensearch.statement.AbstractOpenSearchStatement;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.AbstractSqlGenerator;

public class OpenSearchGenerator extends AbstractSqlGenerator<AbstractOpenSearchStatement> {

    @Override
    public boolean supports(final AbstractOpenSearchStatement statement, final Database database) {
        return OpenSearchLiquibaseDatabase.PRODUCT_NAME.equals(database.getDatabaseProductName());
    }

    @Override
    public ValidationErrors validate(final AbstractOpenSearchStatement statement, final Database database,
                                     final SqlGeneratorChain<AbstractOpenSearchStatement> sqlGeneratorChain) {
        return new ValidationErrors();
    }

    @Override
    public Sql[] generateSql(final AbstractOpenSearchStatement statement, final Database database, final SqlGeneratorChain<AbstractOpenSearchStatement> sqlGeneratorChain) {
        return new Sql[0];
    }

}
