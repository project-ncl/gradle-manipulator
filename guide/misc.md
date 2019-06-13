---
title: Miscellaneous Manipulations
---

* Contents
{:toc}

#### Repository Extraction

If the property `repoRemovalBackup` is defined (default value: off), GME will extract all repository sections from the build. If it is set to
* `settings.xml` a backup of any extracted sections will be created in the top level directory.
* `<path to file>` a backup of any extracted sections will be created in the specified file.
