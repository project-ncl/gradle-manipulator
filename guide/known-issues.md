---
title: Known Issues
---

* Contents
{:toc}

#### Spring Dependency Management Plugin

The [Dependency Management Plugin](https://docs.spring.io/dependency-management-plugin/docs/current/reference/html) is
a Gradle plugin that provides Maven-like dependency management and exclusions.

The Dependency Management Plugin controls the versions of the project’s direct and transitive dependencies and, as such,
conflicts with the dependency management functionality in GME. Therefore, dependencies managed by Spring Dependency
Management plugin are not currently supported by GME.

