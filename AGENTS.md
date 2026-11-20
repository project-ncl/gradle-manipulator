# AGENTS.md

This file provides guidance to agents when working with code in this repository.
See also [CONTRIBUTING.md](CONTRIBUTING.md) for build, test, code style, and release details,
and [README.md](README.md) for project overview and module descriptions.

## Build System

- Multi-module Gradle project (analyzer, manipulation, cli, common) — see [README.md](README.md) for full module descriptions
- **CRITICAL**: Must use Gradle 8.3 for releases (`gradleReleaseVersion` property)
- Requires JDK17 to build, JDK8 to run (under Gradle 8.3)
- **JDK8 runtime lock-in**: The `manipulation` plugin is injected into target builds at runtime; this injection mechanism (inherited from PME) constrains the manipulation plugin to JDK 8 at runtime. Do not introduce runtime dependencies that require JDK > 8 in the `manipulation` module.
- **CRITICAL**: Always use the correct JDK for the Gradle version being run — do NOT use the system
  default (JDK 25) as it is incompatible:
  - Gradle >= 8.3 → JDK 17
  - Gradle < 8.3  → JDK 11
- Uses GradleVersion-conditional plugin declarations and reflection where needed to support Gradle 4.10-9.x

## Build & Test Commands

See [CONTRIBUTING.md § Building](CONTRIBUTING.md#building) and
[CONTRIBUTING.md § Running Tests](CONTRIBUTING.md#running-tests) for full command reference.

Key agent reminders:
- Always set `JAVA_HOME` explicitly — the system Java is JDK 25 which is incompatible:
  ```bash
  # Gradle >= 8.3 (default wrapper, releases)
  JAVA_HOME=<path-to-jdk17> ./gradlew clean build

  # Gradle < 8.3 (compatibility testing)
  JAVA_HOME=<path-to-jdk11> ./gradlew clean build
  ```
- Functional tests live in `src/functTest/` (not `src/test/`) and use `./gradlew functionalTest`
- `./gradlew spotlessApply` auto-formats code (also runs before compile by default)

## Code Style & Conventions

See [CONTRIBUTING.md § IDE Config and Code Style](CONTRIBUTING.md#ide-config-and-code-style) and
[CONTRIBUTING.md § Code Recommendations](CONTRIBUTING.md#code-recommendations) for full details.

Key agent reminders:
- **Logging**: MUST use `GMLogger.getLogger(getClass())` — not the standard Gradle logger
- **Exceptions**: `ManipulationException` (checked) internally; `ManipulationUncheckedException` (unchecked) at API boundaries
- **Test runner**: Use `TestUtils.createGradleRunner()` (not `GradleRunner.create()`) — it auto-prefixes system properties with `-D`
- **Test log level**: Use `LoggingRule` to set log level in tests, not standard Gradle logging

## Project-Specific Patterns

- **WireMock dependency**: WireMock 2.x brought Jetty transitively; do not assume Jetty is a direct project dependency.
- **Version compatibility**: Build script uses `GradleVersion.current()` branching for plugin versions (see `build.gradle.kts` `plugins {}` block) with concatenated string IDs on frozen branches to manage Dependabot updates, and reflection for version-dependent APIs (e.g. `IdeaModule`, `Spotless` eclipse config, `GradlePluginDevelopmentExtension`).
- **Shadow plugin**: Different artifact IDs for different Gradle versions (`com.github.johnrengelman.shadow` vs `com.gradleup.shadow`)
- **Jackson multi-release JARs**: Dynamic `jacksonCoreJava8` task strips `META-INF/versions/**` from `jackson-core` to resolve Gradle issue #24390 on older Gradle versions (< 7.6.4) and Java 8; ShadowJar also excludes `META-INF/versions/**`.
- **Module JDK targets**: Root build defaults all projects to Java 8 compatibility; the `cli` module explicitly targets JDK 11 (`cli/build.gradle.kts`).
- **CI fix script**: `gradle/fix-build.sh` modifies build files and tasks for Gradle <8.3 compatibility (e.g. `archiveClassifier`, named tasks, `GradleVersion` package, `@DisableCachingByDefault`)
- **Lombok config**: Generation disabled via `generateLombokConfig` task
- **Test fixtures**: Custom implementation in `src/testFixtures/` (emulates Gradle 5.6+ feature)

## Release Process

See [CONTRIBUTING.md § Releasing](CONTRIBUTING.md#releasing) for full prerequisites and commands.

Key agent reminders:
- Tag is created **before** the build (not after) to ensure the correct SHA in the manifest
- `afterReleaseBuild` publishes to both Gradle Portal and Maven Central
- README.md is auto-updated with the version during release
