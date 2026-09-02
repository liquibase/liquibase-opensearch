# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/liquibase/liquibase-opensearch/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/liquibase/liquibase-opensearch/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.0.1...v0.1.0
[lb-license]: https://www.liquibase.com/blog/liquibase-community-for-the-future-fsl
