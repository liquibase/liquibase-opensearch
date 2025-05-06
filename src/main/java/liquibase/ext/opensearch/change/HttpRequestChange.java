package liquibase.ext.opensearch.change;

import liquibase.change.AbstractChange;
import liquibase.change.DatabaseChange;
import liquibase.database.Database;
import liquibase.ext.opensearch.statement.HttpRequestStatement;
import liquibase.servicelocator.PrioritizedService;
import liquibase.statement.SqlStatement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

@DatabaseChange(name = "httpRequest",
        description = "Execute an arbitrary HTTP request with the provided payload",
        priority = PrioritizedService.PRIORITY_DATABASE)
@NoArgsConstructor
@Getter
@Setter
public class HttpRequestChange extends AbstractChange {

    private String method;
    private String path;
    private String body;

    @Override
    public String getConfirmationMessage() {
        return String.format("executed the HTTP %s request against %s (with a body of size %d)",
                this.getMethod(),
                this.getPath(),
                Optional.ofNullable(this.getBody()).map(String::length).orElse(0));
    }

    @Override
    public SqlStatement[] generateStatements(final Database database) {
        return new SqlStatement[] {
            new HttpRequestStatement(this.getMethod(), this.getPath(), this.getBody())
        };
    }
}
