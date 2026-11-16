![Build status (GitHub Actions)](https://github.com/project-ncl/gradle-manipulator/workflows/CI/badge.svg)

# Table of Contents

<!-- TocDown Begin -->
* [Introduction](#introduction)
* [Module Structure](#module-structure)
  * [analyzer](#analyzer)
  * [manipulation](#manipulation)
  * [cli](#cli)
  * [common](#common)
  * [buildSrc](#buildsrc)
  * [gradle](#gradle)
* [Contributions](#contributions)
* [Documentation](#documentation)
<!-- TocDown End -->

# Introduction

GME (Gradle Manipulator Extension) is a Gradle tool to align versions and dependencies within a project according to
external references. It excels in a cleanroom environment where large numbers of pre-existing projects must be rebuilt.
It is a sibling project to the [Maven POM Manipulator Extension (PME)](https://github.com/project-ncl/pom-manipulation-ext)
and shares the same overall approach (also used in the Ant tooling). Much of the core logic (including the call to
the Dependency Analyzer service) is reused directly from PME code.

Both plugins are compatible with Gradle versions 4.10 through 9.x. Both plugins work in conjunction and therefore the
same version is required for each. The analyzer plugin injects a reference to the manipulation plugin into the target
build.

# Module Structure

## analyzer

A Gradle plugin used during alignment. It analyses the project and calls the Dependency Analyzer (DA) service via
reused PME code, producing a `manipulation.json` metadata file describing the aligned dependencies and project version.

The latest version of the Analyzer init script can be retrieved from
https://repo1.maven.org/maven2/org/jboss/pnc/gradle-manipulator/analyzer/4.0/analyzer-4.0-init.gradle

## manipulation

A Gradle plugin that is **injected into the target build** by the analyzer plugin. It runs during the build and
handles dependency/version overrides and deployment adjustments. Because of this injection mechanism, the manipulation
plugin (and by extension GME as a whole) is locked to JDK 8 at runtime — the same constraint that applies to PME.

## cli

A command-line tool that drives the analyzer plugin over a Gradle build without requiring the caller to modify the
build scripts directly.

## common

Shared code used across all modules: Groovy script support, I/O utilities, logging (`GMLogger`), the data model,
various utility classes, configuration handling, and the dependency cache.

## buildSrc

A standard Gradle mechanism for extracting build logic into compiled code rather than keeping it inline in build
scripts. This project uses it for a custom plugin that manages Maven settings — an approach chosen because the
previously used external plugin had poor support for the required behaviour.

## gradle

Contains the Gradle wrapper (standard for all Gradle projects) plus several supporting files:

- **`dependencies.gradle`** — dependency declarations were moved here so that Dependabot can discover and update them.
  The format is carefully structured to match what Dependabot understands (this took considerable effort to get right).
- **`*.gradle` Groovy scripts** — Groovy is not pre-compiled the way Kotlin is, so version-dependent API calls are
  moved into these Groovy scripts to prevent them from being evaluated before the correct Gradle version is known.
- **`fix-build.sh`** — a script that patches `build.gradle.kts` at CI time to make it compatible with Gradle versions
  older than 8.3, working around syntax and API differences that cannot be handled in the main build file.

# Contributions

Contributions are more than welcome! Before contributing to the project, please read
[CONTRIBUTING.md](https://github.com/project-ncl/gradle-manipulator/blob/main/CONTRIBUTING.md). To contribute sample
Groovy scripts (for this project or the sibling PME project) please see the
[Groovy Examples](https://github.com/project-ncl/manipulator-groovy-examples) project.

# Documentation

Documentation and usage instructions for the project may be found
[here](https://project-ncl.github.io/gradle-manipulator/).
