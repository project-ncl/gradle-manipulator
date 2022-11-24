---
---

* Contents
{:toc}

### Overview

GME (Gradle Manipulation Extension) is a set of Gradle plugins and a CLI that allow the alignment of versions of a Gradle project according to some external preference.

Two plugins make up the extension, the `analyzer` plugin and the `manipulation` plugin.

The job of the `analyzer` plugin is to generate a metadata file that contains altered dependencies for each project of a Gradle Project.
The file is then meant to be used by the `manipulation` plugin to enforce the versions of the dependencies that are captured in the metadata file.

The metadata file is named `manipulation.json` and is created by the `generateAlignmentMetadata` task of the `alignment` plugin in the root directory of the target Gradle project. The file is in JSON format, may be read and written through [this](https://www.javadoc.io/static/org.jboss.gm/common/2.8/org/jboss/gm/common/io/ManipulationIO.html) utility class. The Java source may be found [here](https://github.com/project-ncl/gradle-manipulator/blob/master/common/src/main/java/org/jboss/gm/common/model/ManipulationModel.java) and the documentation is [here](https://www.javadoc.io/doc/org.jboss.gm/common/latest/org/jboss/gm/common/model/ManipulationModel.html). An example from the functional tests of the `manipulation.json` is [here](https://github.com/project-ncl/gradle-manipulator/blob/master/manipulation/src/functTest/resources/simple-project/manipulation.json).

### Usage

#### Applying the Plugin(s)

There are multiple ways that the plugins can be applied.

* Utilise the below CLI (this is the **recommended** approach).
* Add plugin configuration to project manually.
* Apply a script to project that handles all the details (this method is used by the `analyzer` plugin which configures the project to use the `manipulation` plugin automatically).
* Use an init script (see below).

It should also be noted that the project itself contains a properly configured init script for the `analyzer` plugin (which gets released along with the plugin).
Furthermore, when the `analyzer` plugin executes, it alters the main gradle script of the target project to include the manipulation plugin.

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Plugin removal and handling of the Gradle-Semanatic-Build-Versioning plugin is only available through the CLI.
</td>
</tr>
</table>


##### CLI

The CLI (available after version 1.4) is capable of running arbitrary groovy scripts prior to the build. See [here](https://project-ncl.github.io/gradle-manipulator/guide/groovy.html) for further details on this.

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

##### Init Script

If a development version is being used, the init script is placed during the build into
`analyzer/build/resources/main/analyzer-init.gradle`. If a released version is being used, it is deployed as
`analyzer-<version>-init.gradle`and may be found in Maven Central, i.e., for version 2.7,
[https://repo1.maven.org/maven2/org/jboss/gm/analyzer/2.7/analyzer-2.7-init.gradle](https://repo1.maven.org/maven2/org/jboss/gm/analyzer/2.7/analyzer-2.7-init.gradle).

Now, by executing

```
./gradlew --info --init-script analyzer-init.gradle generateAlignmentMetadata -DrestURL=http://some.da.server
```

you should get the `manipulation.json` file in the root of the project.

For detailed documentation on the parameters please see [here](https://project-ncl.github.io/gradle-manipulator/).

#### Troubleshooting

* Gradle build daemon disappeared unexpectedly
   * This may happen in Gradle versions prior to 5.x if the shell environment contains non ASCII characters (e.g. the PROMPT symbol).

### Alignment Configuration

#### Customising the Manipulation Plugin Version

<table bgcolor="red">
<tr>
<td>
    Warning : This option may lead to Alignment and Manipulation plugins being 'out of sync'. Therefore caution should be used when applying this option and only use it if absolutely necessary.
</td>
</tr>
</table>
When running the alignment plugin (e.g. via the CLI), it is possible to configure the version of the Manipulation Plugin that is injected. By default the same version as the alignment plugin will be used (i.e. the current version). By setting `manipulationVersion` to a value (e.g. `2.6.`) this version is requested instead.

#### Unresolved Dependencies

By default, the alignment phase is set to fail when it encounters any unresolvable dependencies. For example, if the
dependency `com.redhat:unresolved-dependency` in configuration `default` is unable to be resolved, then the alignment
phase will fail with an error message indicating which dependencies were unresolvable.

```
For configuration default, unable to resolve all project dependencies: [com.redhat:unresolved-dependency:]
```

To ignore any unresolvable dependencies, set the property `ignoreUnresolvableDependencies` to `true`. This allows any
unresolved dependencies to be skipped, thereby allowing any resolved dependencies to be aligned. You will see a warning
message indicating which unresolved dependencies were ignored.

```
For configuration default, ignoring all unresolved dependencies: [com.redhat:unresolved-dependency:]
```

Even though the alignment is successful at this point, the build itself will fail if the dependency remains unresolved.

For more details on Gradle dependencies and configurations, see [Declaring dependencies](https://docs.gradle.org/current/userguide/declaring_dependencies.html) and [Resolvable and consumable configurations](https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:resolvable-consumable-configs) in the Gradle user manual.

### General Configuration

#### Disabling the Plugins

You can disable GME using the `manipulation.disable` property:

    $ gradle -Dmanipulation.disable=true...

#### Logging

The tool uses its own logging system (that backs onto the Gradle logging system).

* If `loggingClassnameLineNumber` is set to true this will add classname and line numbers to messages (default: true).
* If `loggingColours` is set to true it will also use colours (default: true).
* If `loggingLevel` is set to true it will output the logging category e.g. INFO. (default: false).

#### Summary Logging

GME will output a summary of its changes at the end of the run. As well as reporting version and dependency alignment,
it is also possible to report what _hasn't_ been aligned by setting the property `reportNonAligned=true`. This summary
may also be output to a file by setting the property `reportTxtOutputFile` to the name of the file, e.g.,
`alignmentReport.txt`. The file's path will always be relative to the execution root `build` directory.

Finally, it will also output the comparator summary as a JSON file. The file's path will always be relative to the
execution root `build` directory. By default, the file will be named `alignmentReport.json`. However, the name of this
file may be changed by setting the `reportJSONOutputFile` property to an alternate name for the file.

    {
      "executionRoot" : {
        "groupId" : "org.foo",
        "artifactId" : "foo-parent",
        "version" : "7.0.0.Final-rebuild-1",
        "originalGAV" : "org.foo:foo-parent:7.0.0.Final"
      },
      "modules" : [ {
        "gav" : {
          "groupId" : "org.foo",
          "artifactId" : "foo-parent",
          "version" : "7.0.0.Final-rebuild-1",
          "originalGAV" : "org.foo:foo-parent:7.0.0.Final"
        },
        ...

This JSON file may be read as POJO by using the [JSONUtils](https://github.com/release-engineering/pom-manipulation-ext/blob/master/common/src/main/java/org/commonjava/maven/ext/common/util/JSONUtils.java)
class which utilises the [json](https://github.com/release-engineering/pom-manipulation-ext/blob/master/common/src/main/java/org/commonjava/maven/ext/common/json)
package.

### OpenTelemetry Instrumentation

If `OTEL_EXPORTER_OTLP_ENDPOINT` is defined (and optionally `OTEL_SERVICE_NAME`) then OpenTelemetry instrumentation
will be activated. It will read trace information from the environment as described [here](https://github.com/jenkinsci/opentelemetry-plugin/blob/master/docs/job-traces.md#environment-variables-for-trace-context-propagation-and-integrations) and will propagate the information via headers in any REST calls.

### Feature Guide

Below are links to more specific information about configuring sets of features in GME:

* [Project version manipulation](guide/project-version-manip.html)
* [Dependency manipulation](guide/dep-manip.html)
* [Artifact publishing](guide/artifact-publishing.html)
* [Groovy](guide/groovy.html)
* [Plugins](guide/plugins.html)
* [Repositories](guide/misc.html)
* [Known Issues](guide/known-issues.html)
