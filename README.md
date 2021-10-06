![Build status (GitHub Actions)](https://github.com/project-ncl/gradle-manipulator/workflows/CI/badge.svg)

# Table of Contents

<!-- TocDown Begin -->
* [Introduction](#introduction)
* [Plugins](#plugins)
  * [Analyzer](#analyzer)
  * [Manipulation](#manipulation)
  * [Contributions](#contributions)
  * [Documentation](#documentation)
<!-- TocDown End -->

# Introduction

This is a Gradle tool to align versions and dependencies within the project according to some external references. It excels in a cleanroom environment where large numbers of pre-existing projects must be rebuilt. It is a sibling project to the [Maven POM Manipulator Extension](https://github.com/release-engineering/pom-manipulation-ext).

# Plugins

Both plugins work in conjunction and therefore the same version is required for each. The analyzer plugin will inject a reference
to the manipulation plugin. It is compatible (to run or build) with Gradle 4.10.x - 7.x.

## Analyzer

The `analyzer` directory contains the gradle plugin that generates metadata information about aligned dependencies and the project version.

Latest version of the Analyzer init script : https://repo1.maven.org/maven2/org/jboss/gm/analyzer/2.9/analyzer-2.9-init.gradle


## Manipulation

The `manipulation` directory contains the gradle plugin that uses the metadata information generated by the `alignment` plugin and
modifies the project to use those dependencies and project version.

## Contributions

Contributions are more than welcome! Before contributing to the project, please read [this](https://github.com/project-ncl/gradle-manipulator/blob/main/CONTRIBUTING.md). To contribute sample Groovy scripts (for this project or the sibling PME project) please see the [Groovy Examples](https://github.com/project-ncl/manipulator-groovy-examples) project.

## Documentation

Documentation and usage instructions for the project may be found [here](https://project-ncl.github.io/gradle-manipulator/).
