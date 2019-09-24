---
title: Miscellaneous Manipulations
---

* Contents
{:toc}

#### Repository Extraction

GME can export artifact repositories used by the project into a backup file
in the project's root directory. This file uses Maven's settings.xml format.

This is controlled by the property `repoRemovalBackup` (default value: empty i.e. off).
The target file name (or path) can be modified via this system property, e.g.:

```
./gradlew generateAlignmentMetadata -DrepoRemovalBackup=repositories-backup.xml
```

Relative paths will be resolved against project's root directory.
