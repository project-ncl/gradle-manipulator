---
title: "Project Version Manipulation"
---

* Contents
{:toc}

### Overview

The tool uses the same code from PME and therefore supports a subset of the features from [here](https://release-engineering.github.io/pom-manipulation-ext/guide/project-version-manip.html)

### Disabling Version Manipulation

If `versionModificationEnabled` is set to false (default: true) then no version change will happen.

### Automatic version increment

The extension can be used to append a version suffix/qualifier to the current project, and then apply an incremented index to the version to provide a unique release version.  For example, if the current project version is 1.0.0.GA, the extension can automatically set the version to 1.0.0.GA-rebuild-1, 1.0.0.GA-rebuild-2, etc. This is enabled by default and the value set to `redhat`.

The extension is configured using the property `versionIncrementalSuffix`.

Note that if `manipulation.json` already exists then this will also be used as a source of information to calculate the increment value.

#### Version increment padding

When using the automatic increment it is also possible to configure padding for the increment. For instance, by setting `versionIncrementalSuffixPadding` to `3` the version will be `rebuild-003`. This is enabled by default and set to 5.

### Snapshot Detection

The tool can detect snapshot versions and either preserve the snapshot or replace it with a real version. This is controlled by the property `versionSuffixSnapshot`. The default is false (i.e. remove SNAPSHOT and replace by the suffix).
