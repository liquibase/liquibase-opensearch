package liquibase.nosql.snapshot;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.ext.opensearch.database.OpenSearchLiquibaseDatabase;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorChain;
import liquibase.structure.DatabaseObject;

import java.util.ResourceBundle;

import static liquibase.plugin.Plugin.PRIORITY_SPECIALIZED;

public class NoSqlSnapshotGenerator implements SnapshotGenerator {
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("liquibase/i18n/liquibase-opensearch");

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof OpenSearchLiquibaseDatabase) {
            return PRIORITY_SPECIALIZED;
        }
        return PRIORITY_NONE;
    }

    @Override
    public <T extends DatabaseObject> T snapshot(T example, DatabaseSnapshot snapshot, SnapshotGeneratorChain chain) throws DatabaseException, InvalidExampleException {
        throw new DatabaseException(String.format(resourceBundle.getString("command.unsupported"), "db-doc, diff*, generate-changelog, and snapshot*"));
    }

    @Override
    public Class<? extends DatabaseObject>[] addsTo() {
        return new Class[0];
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[0];
    }
}
