# Liquibase Extension for OpenSearch

This [Liquibase] extension supports managing migrations for [OpenSearch].

> [!TIP]
> If you wish to use Spring Boot you should be using [`liquibase-opensearch-spring-boot-starter`] instead of using this directly.

## Usage
This supports a single liquibase change type called `httpRequest` which executes the given request against OpenSearch.
A simple example changelog might look like this:
```yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: test
      comment: this creates the index testindex and stores a document in it
      changes:
        - httpRequest:
            method: PUT
            path: /testindex
            body: >
              {
                "mappings": {
                  "properties": {
                    "testfield": {
                      "type": "text"
                    }
                  }
                }
              }
        - httpRequest:
            method: PUT
            path: /testindex/_doc/testdoc
            body: >
              {
                "testfield": "foo"
              }
```

### OpenSearch Connection

#### New Connection From Liquibase

The standard liquibase integration supports only connections with either HTTP or HTTPS with valid TLS certificates and
either no authentication or basic authentication (username/password). To use this you have to prepend the URL(s) of
OpenSearch with `opensearch:` so that Liquibase can identify it as an OpenSearch connection:
```java
void main() {
    final var url = "opensearch:http://localhost:9200";
    final var username = "...";
    final var password = "...";

    final var changeLogFile = "path/to/changelog.yaml";

    // let Liquibase instantiate the connection
    final var database = (OpenSearchLiquibaseDatabase) DatabaseFactory.getInstance().openDatabase(url, username, password, null, null);

    // execute the migration
    new CommandScope(UpdateCommandStep.COMMAND_NAME)
            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, changeLogFile)
            .execute();
}
```

This also supports multiple URLs for OpenSearch, in which case you can specify them in a comma-separated manner. Note
that they all must be for the same OpenSearch cluster, otherwise you'll have undefined behaviour!
Example: `opensearch:http://localhost:9200,http://localhost:9201,http://localhost:9202`

#### Custom `OpenSearchClient`

If you wish to use any other form of authentication
or have special requirements for the connection (e.g. not validate the TLS certificates) you have to [construct your own
`OpenSearchClient`][custom-client] and pass that to `OpenSearchConnection`. See [CustomOpenSearchClientLiquibaseIT] for a full example.

```java
void main() {
    final var changeLogFile = "path/to/changelog.yaml";

    final var connection = new OpenSearchConnection(openSearchClient);
    ConnectionServiceFactory.getInstance().register(connection);
    final var database = new OpenSearchLiquibaseDatabase(connection);
    DatabaseFactory.getInstance().register(database);

    // execute the migration
    new CommandScope(UpdateCommandStep.COMMAND_NAME)
            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, changeLogFile)
            .execute();
}
```

## OpenSearch Compatibility

`liquibase-opensearch` is currently compatible with OpenSearch 2.x and 3.x.

## Versioning

This project adheres to [Semantic Versioning].

## Changelog
For the changelog please see the dedicated [CHANGELOG.md](CHANGELOG.md).

## License
This project is licensed under the Apache License Version 2.0 - see the [LICENSE] file for details.

[Liquibase]: https://www.liquibase.com/
[OpenSearch]: https://opensearch.org/
[`liquibase-opensearch-spring-boot-starter`]: https://github.com/liquibase/liquibase-opensearch-springboot-starter/
[custom-client]: https://docs.opensearch.org/docs/latest/clients/java/
[CustomOpenSearchClientLiquibaseIT]: src/test/java/liquibase/ext/opensearch/CustomOpenSearchClientLiquibaseIT.java
[Semantic Versioning]: https://semver.org/spec/v2.0.0.html
[LICENSE]: LICENSE
