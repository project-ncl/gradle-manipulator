---
title: "Project Version Manipulation"
---

* Contents
{:toc}

### Overview

When rebuilding a project's sources from a release tag (or really for any version that has already been released), it's
important **NOT** to republish the original GAV (groupId, artifactId, version) coordinate. If you change anything during
your rebuild, you could produce a result that is not a binary equivalent of the original release. To help avoid this,
GME supports automatically updating the project version to append a serial rebuild suffix. GME's re-versioning feature
may also make other changes to the project version, in order to make the resulting version OSGi-compliant where
possible.

### Disabling Version Manipulation

If `versionModification` is set to false (default: true) then no version change will happen.

### Automatic version increment

The extension can be used to append a version suffix/qualifier to the current project, and then apply an incremented
index to the version to provide a unique release version.  For example, if the current project version is 1.0.0.GA, the
extension can automatically set the version to 1.0.0.GA-rebuild-1, 1.0.0.GA-rebuild-2, etc. This is enabled by default
and the value set to `redhat`.

The extension is configured using the property `versionIncrementalSuffix`.

Note that if `manipulation.json` already exists then this will also be used as a source of information to calculate the
increment value.

#### Version increment padding

When using the automatic increment it is also possible to configure padding for the increment. For instance, by setting
`versionIncrementalSuffixPadding` to `3` the version will be `rebuild-003`. This is enabled by default and set to `5`.

### Version suffix

The version suffix to be appended to the current project can be manually selected using the property `versionSuffix`.
Note that `versionSuffix` takes precedence over `versionIncrementalSuffix`.

### Version override

The version can be forcibly overridden by using the property `versionOverride`.  Note that the `versionOverride`
property is meant to be used to override the version only, not the suffix. If the version already contains a suffix, the
result may not be as expected due to automatic version increment. If you wish to override both the version and the
suffix, you need to combine the `versionOverride` and `versionSuffix` properties.

### Snapshot detection

The tool can detect snapshot versions and either preserve the snapshot or replace it with a real version. This is
controlled by the property `versionSuffixSnapshot`. The default is false (i.e., remove `SNAPSHOT` and replace by the
suffix).

### OSGi compliance

If version manipulation is enabled, the extension will also attempt to format the version to be OSGi compliant. For
example, if the versions are:

    1
    1.3
    1.3-GA
    1.3.0-GA

it will change to

    1.0.0
    1.3.0
    1.3.0.GA
    1.3.0.GA

This is controlled by the property `versionOsgi`. The default is true (i.e., make the versions OSGi compliant).

### Version suffix alternatives

It is possible to pass in a comma separated list of alternate suffixes via the property `versionSuffixAlternatives`. The
default value `redhat` will be applied if the current suffix does not match. This is used during dependency alignment to
validate strict alignment between differing suffix types (from the input REST or BOM data).
