---
title: Miscellaneous Manipulations
---

* Contents
{:toc}

#### Repository Extraction

By default, GME will export artifact repositories used by the project into `repositories-backup.xml`
file in the project's root directory. This file uses Maven's settings.xml format.

The target file name (or path) can be modified via `repoRemovalBackup` system property, e.g.:

```
./gradlew generateAlignmentMetadata -DrepoRemovalBackup=repositories.xml
```

Relative paths will be resolved against project's root directory.

If the `repoRemovalBackup` system property is set to an empty value, repository extraction is disabled.
