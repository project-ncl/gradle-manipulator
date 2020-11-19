---
---

* Contents
{:toc}

### Overview

GME (Gradle Manipulation Extension) is a set of Gradle plugins and a CLI that allow the alignment of versions of a Gradle project according to some external preference.

Two plugins make up the extension, the `analyzer` plugin and the `manipulation` plugin.

The job of the `analyzer` plugin is to generate a metadata file that contains altered dependencies for each project of a Gradle Project.
The file is then meant to be used by the `manipulation` plugin to enforce the versions of the dependencies that are captured in the metadata file.

The metadata file is named `manipulation.json` and is created by the `generateAlignmentMetadata` task of the `alignment` plugin in the root directory of the target Gradle project.

### Usage

#### CLI

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : The CLI is only available from version 1.4.
</td>
</tr>
</table>

The CLI is capable of running arbitrary groovy scripts prior to the build. See [here](https://project-ncl.github.io/gradle-manipulator/guide/groovy.html) for further details on this.

```
Usage: GradleAnalyser [-dhV] [-l=<installation>] -t=<target>
                      [-D=<String=String>]...
CLI to optionally run Groovy scripts and then invoke Gradle.
  --[no-]colour           Enable (or disable with '--no-colour') colour output
                            on logging.
  -d                      Enable debug.
  -D=<String=String>      Pass supplemental arguments (e.g. groovy script
                            commands)
  -h, --help              Show this help message and exit.
  -l=<installation>       Location of Gradle installation.
  -t, --target=<target>   Target Gradle directory.
  -V, --version           Print version information and exit.
```

Apart from the Groovy scripting, the CLI can modify colour output, enable debug logging, pass extra `-D` parameters, specify the Gradle distribution to use and specify the target directory to operate on.

Note that the CLI will capture [unmatched arguments](https://picocli.info/#unmatched-annotation) and pass them directly to the Gradle build. For example, below `--stacktrace`, `--init-script` and the task exclusion of `-x task-to-exclude` are all passed to Gradle.

```
java -jar cli.jar --target=<gradle-project> -d
    --stacktrace
    --init-script=analyzer/build/resources/main/analyzer-init.gradle
    -DrepoRemovalBackup=repositories-backup.xml
    -DrestURL=http://da.rest.url.com/da/rest/v-1
    -DignoreUnresolvableDependencies=true
    -DversionIncrementalSuffix=temporary-redhat
    -DrestRepositoryGroup=DA-temporary-builds
    -DgroovyScripts=file:///tmp/fixup.groovy
    generateAlignmentMetadata
    -x task-to-exclude
```

It is possible to run the Gradle process using a different JDK by passing in the following parameter:

    -Dorg.gradle.java.home=<JDK-Location>


To obtain the CLI it may be downloaded from Maven Central [here](https://repo1.maven.org/maven2/org/jboss/gm/cli).

##### Disabling the alignment

You can disable GME _through the CLI_ using the `manipulation.disable` property:

	$ gradle -Dmanipulation.disable=true...

#### Applying the Plugin(s)

There are multiple ways that the plugins can be applied.

* Utilise the above CLI.
* Add plugin configuration to project manually
* Apply script to project that handles all the details (this method is used by the `analyzer` plugin which configures the project to use the `manipulation` plugin automatically)
* Use an init script (see example [here](https://github.com/project-ncl/gradle-manipulator#testing-on-a-real-project))

It should also be noted that the project itself contains a properly configured init script for the `analyzer` plugin (which gets released along with the plugin).
Furthermore, when the `analyzer` plugin executes, it alters the main gradle script of the target project to include the manipulation plugin.

##### Troubleshooting

* Gradle build daemon disappeared unexpectedly
   * This may happen in Gradle versions prior to 5.x if the shell environment contains non ASCII characters (e.g. the PROMPT symbol).

### General Configuration

#### Unresolved Dependencies
If the tool is not able to resolve certain dependencies then it may fail during the alignment phase. Set `ignoreUnresolvableDependencies` to true to ignore those (default: false).

#### Logging

The tool uses its own logging system (that backs onto the Gradle logging system).

* If `loggingClassnameLineNumber` is set to true this will add classname and line numbers to messages (default: true).
* If `loggingColours` is set to true it will also use colours (default: true).
* If `loggingLevel` is set to true it will output the logging category e.g. INFO. (default: false).

### Feature Guide

Below are links to more specific information about configuring sets of features in GME:

* [Project version manipulation](guide/project-version-manip.html)
* [Dependency manipulation](guide/dep-manip.html)
* [Artifact publishing](guide/artifact-publishing.html)
* [Groovy](guide/groovy.html)
* [Repositories Etc.](guide/misc.html)
