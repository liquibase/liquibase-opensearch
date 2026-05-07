# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/liquibase/liquibase-opensearch/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/liquibase/liquibase-opensearch/compare/v0.0.1...v0.1.0
