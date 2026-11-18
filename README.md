# Liquibase Extension for OpenSearch

This [Liquibase] extension supports managing migrations for [OpenSearch].

> [!TIP]
> If you wish to use Spring Boot you should be using [`liquibase-opensearch-spring-boot-starter`] instead of using this
> directly.

## Usage

### Supported Liquibase Change Types

This supports a single liquibase change type called `httpRequest` which executes the given request against OpenSearch.
Other change types are not supported.

All examples shown here are YAML files, however all file types supported by Liquibase are supported.

A simple changelog might look like this:
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

Some APIs - like the bulk API - requires a different content type, this can be set as well:
```yaml
        - httpRequest:
            method: POST
            contentType: application/x-ndjson
            path: /testindex/_bulk
            body: |
              { "create": {} }
              { "testfield": "a" }
              { "create": {} }
              { "testfield": "b" }
```

`contentType` is optional, the default is `application/json`. `body` is optional, if it is omitted an empty body is
sent (e.g. for `DELETE` requests):
```yaml
        - httpRequest:
            method: DELETE
            path: /testindex
```

All other fields are mandatory. `method` must be one of `POST`, `PUT`, `DELETE` or `PATCH` (other methods can't modify
data in OpenSearch) and `path` must start with a `/`. Invalid changes are reported when the changelog is validated,
i.e. before any changeset runs.

### Supported Preconditions

The Liquibase preconditions which don't need to access the database work as usual: `and`, `or`, `not`, `dbms`,
`runningAs`, `changeLogPropertyDefined`, `changeSetExecuted` and `customPrecondition` (incl. `onFail`/`onError`).
The preconditions which are specific to SQL databases (e.g. `tableExists`, `columnExists`, `sqlCheck`, `rowCount`)
are not supported.

Instead, this extension offers the generic `httpCheck` precondition: it executes an HTTP request against OpenSearch
and holds if the response has the expected status code and (optionally) the response body contains the expected value
at the given [JSON pointer]:
```yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: test
      preConditions:
        - onFail: MARK_RAN
        # the index must exist
        - httpCheck:
            method: HEAD
            path: /testindex
        # the field must be mapped as `keyword`
        - httpCheck:
            path: /testindex/_mapping
            jsonPointer: /testindex/mappings/properties/testfield/type
            expectedValue: keyword
        # no document may match the query
        - httpCheck:
            method: POST
            path: /testindex/_count
            body: >
              { "query": { "term": { "testfield": "foo" } } }
            jsonPointer: /count
            expectedValue: "0"
      changes:
        - httpRequest:
            # ...
```

The same in XML:
```xml
<preConditions onFail="MARK_RAN">
    <opensearch:httpCheck method="HEAD" path="/testindex"/>
    <opensearch:httpCheck path="/testindex/_mapping" jsonPointer="/testindex/mappings/properties/testfield/type" expectedValue="keyword"/>
    <opensearch:httpCheck method="POST" path="/testindex/_count" jsonPointer="/count" expectedValue="0">
        <opensearch:body>{ "query": { "term": { "testfield": "foo" } } }</opensearch:body>
    </opensearch:httpCheck>
</preConditions>
```

The fields:
* `path` (mandatory): the path of the request, must start with a `/`.
* `method`: one of `GET` (default), `HEAD` or `POST` (needed by some read APIs like `_search` and `_count`).
* `body` and `contentType`: the optional request body and its content type (default: `application/json`).
* `expectedStatusCode`: the expected HTTP status code (default: `200`). E.g. use `404` to check that an index does not
  exist.
* `jsonPointer` and `expectedValue`: if set (only both together and not with `HEAD`), the value at the JSON pointer of
  the response body must be equal to `expectedValue`. Strings are compared without their quotes, all other values
  (numbers, booleans, `null`, objects and arrays) as compact JSON (e.g. `0`, `true` or `{"a":1}`).

If the response does not match, the precondition fails (handled by `onFail`). If the state can't be checked, it is an
error (handled by `onError`): this is the case if the precondition is invalid, if the request itself fails (e.g. the
connection is lost) or if the response has an unexpected status code which indicates a server error (`5xx`) or missing
permissions (`401`, `403`). This prevents e.g. `onFail: MARK_RAN` from skipping a changeset forever because OpenSearch
was temporarily unavailable. Like all preconditions, `httpCheck` is not part of the checksum of the changeset.

### Unsupported Liquibase Commands

`drop-all` is not supported: for OpenSearch it would mean deleting every index of the cluster. It fails with an error
without deleting anything.

### With the Liquibase CLI

To use this with the [Liquibase CLI] follow these steps:
1. Download & install the Liquibase CLI
2. Download the fat jar of `liquibase-opensearch` from the [GitHub releases page]
3. Put the fat jar in the `lib` folder of the CLI
4. Use the CLI and prefix the URL of OpenSearch with `opensearch:` (e.g. `opensearch:http://localhost:9200`)

The standard liquibase integration supports only connections with either HTTP or HTTPS with valid TLS certificates and
either no authentication or basic authentication (username/password). If you need another form of authentication or need
to use untrusted TLS certificates you have to use Java and construct your own custom `OpenSearchClient` (see below for
an example).

### In a Java Project

To use this in your java project, add a dependency to `org.liquibase.ext:liquibase-opensearch`.

#### OpenSearch Connection

##### New Connection From Liquibase

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

##### Custom `OpenSearchClient`

If you wish to use any other form of authentication
or have special requirements for the connection (e.g. not validate the TLS certificates) you have to [construct your own
`OpenSearchClient`][custom-client] and pass that to `OpenSearchConnection`. See [CustomOpenSearchClientLiquibaseIT] for a full example.

You retain ownership of such a client: closing the `OpenSearchConnection` (or the `OpenSearchLiquibaseDatabase`) does
not close it. Once you no longer need the client, close the transport you created it with (`OpenSearchTransport#close()`),
otherwise its HTTP connections and I/O threads keep running. Connections opened by liquibase from a URL close their
transport when the connection is closed.

```java
void main() throws IOException {
    final var changeLogFile = "path/to/changelog.yaml";

    try (final var transport = buildYourTransport()) {
        final var openSearchClient = new OpenSearchClient(transport);
        final var connection = new OpenSearchConnection(openSearchClient);
        final var database = new OpenSearchLiquibaseDatabase(connection);

        // execute the migration
        new CommandScope(UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, changeLogFile)
                .execute();
    }
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
[Liquibase CLI]: https://docs.liquibase.com/secure/reference-guide-5-1/parameters/working-with-command-parameters
[GitHub releases page]: https://github.com/liquibase/liquibase-opensearch/releases
[JSON pointer]: https://datatracker.ietf.org/doc/html/rfc6901
[custom-client]: https://docs.opensearch.org/docs/latest/clients/java/
[CustomOpenSearchClientLiquibaseIT]: src/test/java/liquibase/ext/opensearch/CustomOpenSearchClientLiquibaseIT.java
[Semantic Versioning]: https://semver.org/spec/v2.0.0.html
[LICENSE]: LICENSE
