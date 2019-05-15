---
---

* Contents
{:toc}

### Overview

GME (Gradle Manipulation Extension) is a set of Gradle plugins that allow the alignment of versions of a Gradle project according to some external preference.

Two plugins make up the extension, the `analyzer` plugin and the `manipulation` plugin.

The job of the `analyzer` plugin is to generate a metadata file that contains altered dependencies for each project of a Gradle Project.
The file is then meant to be used by the `manipulation` plugin to enforce the versions of the dependencies that are captured in the metadata file.

The metadata file is named `manipulation.json` and is created by the `generateAlignmentMetadata` task of the `alignment` plugin in the root directory of the target Gradle project.

### Usage

#### Applying the Plugin(s)

There are multiple ways that the plugins can be applied.

* Add plugin configuration to project manually 
* Apply script to project that handles all the details (this method is used by the `analyzer` plugin is run which configures the project to use the `manipulation` plugin automatically)
* Use an init script (see example [here](https://github.com/project-ncl/gradle-manipulator#testing-on-a-real-project))

### Feature Guide

Below are links to more specific information about configuring sets of features in GME:

* [Project version manipulation](guide/project-version-manip.html)
* [Dependency manipulation](guide/dep-manip.html)
