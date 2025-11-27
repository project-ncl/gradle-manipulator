---
title: Groovy Script Injection
---

* Contents
{:toc}

### Overview

The tool offers the ability to run arbitrary groovy scripts on the sources prior to running the build. This allows GME to be extensible by the user and to carry out non-generic modifications.


### Configuration

If the property `-DgroovyScripts=<value>,....` is set, it will load the remote Groovy script file.

The argument should a comma separated list of HTTP / HTTPS URLs.


### Groovy Scripts

Each groovy script will be run on the execution root (i.e. where Gradle is invoked).

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of GME 5.0 the API has changed: <i>org.jboss.pnc.gradlemanipulator.common.groovy.BaseScript</i> instead of <i>org.jboss.gm.common.groovy.BaseScript</i> and <i>org.jboss.pnc.mavenmanipulator.core.groovy</i> instead of <i>org.commonjava.maven.ext.core.groovy</i>.  The Atlas dependency has also changed its groupId from <i>org.commonjava.maven.atlas.ident.ref.SimpleProjectRef</i> to <i>org.commonjava.atlas.maven.ident.ref.SimpleProjectRef</i>
</td>
</tr>
</table>

Each script <b>must</b> use the following annotations:

```groovy
import org.jboss.pnc.mavenmanipulator.core.groovy.GMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.gradlemanipulator.common.groovy.BaseScript

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@GMEBaseScript BaseScript gmeScript

```

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Prior to GME 1.4, the annotations used the simpler <a href="http://docs.groovy-lang.org/latest/html/gapi/groovy/transform/BaseScript.html">BaseScript</a> annotation e.g. <code>@BaseScript GMEBaseScript gmeScript</code>
</td>
</tr>
</table>

### Invocation Stages

In the example script, we saw the use of the `@InvocationPoint` annotation which controls when the script is run. It
takes a single argument, `invocationPoint`, with the type `InvocationStage`. The possible values for `InvocationStage`
are `PREPARSE`, `FIRST`, `LAST`, and `ALL`. These values are relative to when the manipulations to the build files are
made. The table below provides a description of the invocation stages available for running a script.

| Stage      | Description                                                                                                                                                                              | Since      |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| `PREPARSE` | Alias for `FIRST`.                                                                                                                                                                       | 2.10       |
| `FIRST`    | Runs the script before any other manipulators. It is safe to modify build files on disk during this stage.                                                                               | 1.5        |
| `LAST`     | Runs the script after any other manipulators.                                                                                                                                            | 1.5        |
| ~~`BOTH`~~ | Runs the script during stages `FIRST` and `LAST`. _Note that as of version 2.10, `BOTH` has been replaced by `ALL`_.                                                                     | [1.5, 2.9] |
| `ALL`      | Runs the script during _all_ possible stages: `FIRST`, and `LAST`.  The `getInvocationStage()` API can be used to determine in which stage the script is currently running.              | 2.10       |

### API

The following API is made available:

| Method | Description                           | Since | Valid in Stage |
| -------|:--------------------------------------|:------|:------------------|
| [Project](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html) getProject() | Return the root Project.              | 1.0   | `LAST`            |
| [ManipulationModel](https://github.com/project-ncl/gradle-manipulator/blob/master/common/src/main/java/org/jboss/gm/common/model/ManipulationModel.java) getModel() | Return the current ManipulationModel. | 1.0 | `LAST`            |
| [Properties](https://docs.oracle.com/javase/7/docs/api/java/util/Properties.html) getUserProperties() | Get the user properties. | 1.4 | `ALL`             |
| [File](https://docs.oracle.com/javase/7/docs/api/java/io/File.html) getBaseDir() | Get the working directory (the execution root). | 1.4 | `ALL`             |
| [InvocationStage](https://github.com/release-engineering/pom-manipulation-ext/blob/master/core/src/main/java/org/commonjava/maven/ext/core/groovy/InvocationStage.java) getInvocationStage() | Return the current stage of the groovy manipulation. | 1.4 | `ALL`             |
| [Logger](https://www.javadoc.io/doc/org.slf4j/slf4j-api/1.7.30/org/slf4j/Logger.html) getLogger() | Get the Logger. | 2.1 | `ALL`             |
| [Translator](https://www.javadoc.io/doc/org.commonjava.maven.ext/pom-manipulation-io/latest/org/commonjava/maven/ext/io/rest/Translator.html) getRESTAPI() | Get the REST Version Translator. | 2.1 |  `ALL` |

When running as `FIRST`, Gradle has not parsed and created the Project which means the `getModel`/`getProject` calls are not available. However, it is possible to amend the Gradle scripts directly on disk which will then be read as part of the following alignment process.

The API can then be invoked by e.g.

```groovy
gmeScript.getProject()
```

A typical groovy script that alters a JSON file on disk might be:

```groovy
import org.jboss.pnc.mavenmanipulator.core.groovy.GMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.gradlemanipulator.common.groovy.BaseScript
import org.apache.commons.lang.StringUtils
import org.jboss.pnc.mavenmanipulator.common.model.ManipulationModel
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef

@InvocationPoint(invocationPoint = InvocationStage.LAST)
@GMEBaseScript BaseScript gmeScript

println "Running Groovy script on " + gmeScript.getProject()
println "\tgroovy found new version is " + gmeScript.getModel().getVersion()

String newVersion = gmeScript.getModel().getVersion()
File information = new File(gmeScript.getProject().getRootDir(), "gradle/base-information.gradle")

def newContent = information.text.replaceAll( "(new HibernateVersion[(]\\s')(.*)(',\\sproject\\s[)])", "\$1$newVersion\$3")
information.text = newContent

def newJpaVersion = gmeScript.getModel().getAllAlignedDependencies().values().find {it.asProjectRef() == new SimpleProjectRef("javax.persistence", "javax.persistence-api")}.getVersionString()

newContent = information.text.replaceAll( "(new JpaVersion[(]')(.*)('[)])", "\$1$newJpaVersion\$3")
information.text = newContent
newContent = information.text.replaceAll( "version \\+ \"\\.0\"", "version")
information.text = newContent
```

It is possible to use the `Translator` API to call onto the Dependency Anlalyser to make adjustments beyond what GME already provides e.g.

```groovy
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef
import org.jboss.pnc.mavenmanipulator.io.rest.Translator
...
Translator translator = gmeScript.getRESTAPI();
ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org.hibernate:hibernate-core:5.3.7.Final")
Map<ProjectVersionRef, String> result = translator.translateVersions(Collections.singletonList(pvr))
gmeScript.getLogger().warn("Alignment result is {}", result)
File target = gmeScript.getProject().getBuildFile()
newContent = target.text.replaceFirst("classpath \"org.hibernate:hibernate-core:5.3.7.Final", "classpath \"org.hibernate:hibernate-core:" + result.get(pvr))
target.text = newContent
```

#### Grab Annotations

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Supported from GME 3.6
</td>
</tr>
</table>
It is possible to use Grab annotations (e.g. `@Grab('org.zeroturnaround:zt-exec:1.10')`) to utilise other API beyond what is already available via GME and Gradle. Note this is **only** supported in `FIRST` phase.


### Developing Groovy Scripts

To make it easier to develop scripts for both GME (this project) and [PME](https://github.com/release-engineering/pom-manipulation-ext) an example project has been set up. The [manipulator-groovy-examples](https://github.com/project-ncl/manipulator-groovy-examples) provides a framework to develop and test such scripts.

**Note**: To debug Groovy scripts, while it is possible to use a debugger on the CLI for those scripts with invocation point `FIRST`, for those scripts with `LAST` it is not possible to run the CLI _and_ debug on the Groovy script. Instead, run Gradle directly and invoke the plugin like

    gradle
    --no-daemon
    --info
    --stacktrace
    --init-script=<....analyzer-init.gradle>
    ...
    -DgroovyScripts=file://...../script.groovy
    -Dorg.gradle.debug=true

There are two crucial aspects - you need to activate Groovy debugging via `org.gradle.debug=true` and the source must be added to your IDE so it can see it.
