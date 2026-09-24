# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

* `orderExecuted` is now stored for every changelog entry (it was always empty before).
* `liquibase-opensearch` can now handle more than 10 entries in the changelog index (it now correctly accepts an
  unlimited amount of entries).
* `OpenSearchConnection#getConnectionUserName` now returns the username (previously it always returned an empty string).
* `tag` now tags the most recently executed changeset, determined by `orderExecuted`. Previously it went by
  `dateExecuted`, so changesets executed in the same update run could tie and the tag could land on the wrong one.
* Writes to the changelog and lock indices now force a refresh (`refresh=true`) instead of waiting for the next
  scheduled one (`refresh=wait_for`). Previously, if `index.refresh_interval` was set to `-1` on these indices (e.g. via
  an index template), every write blocked until something else triggered a refresh. With the default settings, each
  write also waited up to 1 second.
* A tag set with `tag` and checksums cleared with `clear-checksums` are now visible to subsequent reads right away.
  Previously they only became visible after the next scheduled refresh of the index, so e.g. a `tag-exists` or
  `rollback` issued right afterwards in the same JVM might not see the tag.
* `clear-checksums` now also drops the checksums cached in memory. Previously, subsequent commands in the same JVM
  still saw the old checksums.
* `httpRequest`: `body` is now optional, if it is omitted an empty body is sent. Previously changes without a `body`
  (e.g. `DELETE` requests) failed with a `NullPointerException` at execution time.
* Closing an `OpenSearchConnection` now closes the underlying transport. Previously its HTTP connections and I/O threads
  leaked, making the thread count grow in long-running applications which open multiple connections. Connections
  created with a custom `OpenSearchClient` are not affected: the caller owns its transport and has to close it.
* The fat jar now includes `slf4j-api`, which `httpclient5` needs at runtime. Previously it was missing, so the
  extension failed with `NoClassDefFoundError: org/slf4j/LoggerFactory` in the Liquibase 5.x CLI (the 4.x CLI happened
  to ship slf4j itself).

## [2.1.0] - 2026-09-03

### Changed

* Updated to Liquibase 5.0.4
* Updated to `opensearch-java:3.10.0`
* Migrated from Jackson 2 to Jackson 3 (`tools.jackson.core`). The OpenSearch client is now created with
  `org.opensearch.client.json.jackson3.JacksonJsonpMapper`; Jackson 2 is no longer on the classpath of this extension.

### Fixed

* (Re)creating the database change log now clears the `FastCheckService` cache. This is only relevant if Liquibase is
  used against the same database while between two changelog runs (without stopping the application) the database change
  log is deleted and recreated. In that case, Liquibase would otherwise not detect that the database change log has
  changed and would not re-run changesets that have already been run before. Realistically this only applies to tests.
* `Connection#getURL` now contains the UUID of the cluster to make the value unique. This is needed since it is used as
  a key in the `FastCheckService` cache and otherwise Liquibase would not detect that the database has changed if the
  same connection is used for two different clusters.

## [2.0.0] - 2026-05-28

### Breaking Changes

* Upgraded to Liquibase 5.0.3
  * Note the [license change on Liquibase itself][lb-license]. The license of `liquibase-opensearch` remains unchanged (ALv2)

## [1.0.0] - 2026-05-27

### Added

* The `Content-Type` of the request can now be specified using the optional `contentType` field on `httpRequest`.
  The default is `application/json`, but for bulk requests this has to be set to `application/x-ndjson`

### Fixed

* Changesets are only unique in the changelog filename + `id` + `author` combination, but so far `liquibase-opensearch`
  considered them unique by just their `id`. New changesets are now stored with the correct combination as their ID.

## [0.2.0] - 2026-03-09

### Added

* New releases now also produce a fat JAR for easy usage with the CLI (refer to the README for more details)

### Changed

* Updated to `opensearch:3.7.0`

## [0.1.0] - 2025-08-25

### Changed

* Updated to `opensearch-java` v3 - this is a breaking change for consumers!

[Unreleased]: https://github.com/liquibase/liquibase-opensearch/compare/v2.1.0...HEAD
[2.1.0]: https://github.com/liquibase/liquibase-opensearch/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/liquibase/liquibase-opensearch/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.0.1...v0.1.0
[lb-license]: https://www.liquibase.com/blog/liquibase-community-for-the-future-fsl
