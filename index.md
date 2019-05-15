---
---

* Contents
{:toc}

### Overview

GME (Gradle Manipulation Extension) is a set of Gradle plugins that allow the alignment of versions of a Gradle project according to some external preference.

Two plugins make up the extension, the analyzer plugin and the manipulation plugin.

The job of the analyzer plugin is to generate a metadata file that contains altered dependencies for each project of a Gradle Project.
The file is then meant to be used by the manipulation plugin to enforce the versions of the dependencies that are captured in the metadata file.

### Usage


### Feature Guide

Below are links to more specific information about configuring sets of features in GME:

* [Project version manipulation](guide/project-version-manip.html)
* [Dependency manipulation](guide/dep-manip.html)
