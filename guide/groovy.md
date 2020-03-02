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


Each script <b>must</b> use the following annotations:

```
import org.commonjava.maven.ext.core.groovy.GMEBaseScript
import org.commonjava.maven.ext.core.groovy.InvocationPoint
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.jboss.gm.common.groovy.BaseScript

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@GMEBaseScript BaseScript gmeScript

```

where InvocationStage may be `FIRST`, `LAST` or `BOTH`. This denotes whether the script is ran
before all other manipulators, after or both. The script therefore encodes how and when it is run.

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Prior to GME 1.4 the annotations used the simpler <a href="http://docs.groovy-lang.org/latest/html/gapi/groovy/transform/BaseScript.html">BaseScript</a> annotation e.g. <i>@BaseScript GMEBaseScript gmeScript</i>
</td>
</tr>
</table>


### API

The following API is made available:


| Method | Description |
| -------|:------------|
| [Project](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html) getProject() | Return the root Project. <br/><i>Only valid in InvocationStage.LAST</i> |
| [ManipulationModel](https://github.com/project-ncl/gradle-manipulator/blob/master/common/src/main/java/org/jboss/gm/common/model/ManipulationModel.java) getModel() | Return the current ManipulationModel. <br/><i>Only valid in InvocationStage.LAST</i> |

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : From version 1.4 the following extra API is available:
</td>
</tr>
</table>

| [Properties](https://docs.oracle.com/javase/7/docs/api/java/util/Properties.html) getUserProperties() | Get the user properties. |
| [File](https://docs.oracle.com/javase/7/docs/api/java/io/File.html) getBaseDir() | Get the working directory (the execution root). |
| [InvocationStage](https://github.com/release-engineering/pom-manipulation-ext/blob/master/core/src/main/java/org/commonjava/maven/ext/core/groovy/InvocationStage.java) getInvocationStage() | Return the current stage of the groovy manipulation. |


When running as `FIRST` Gradle has not parsed and created the Project which means the `getModel`/`getProject` calls are not available. However it is possible to ammend the Gradle scripts directly on disk which will then be read as part of the following alignment process.

The API can then be invoked by e.g.

    gmeScript.getProject()


A typical groovy script that alters a JSON file on disk might be:


    import org.jboss.gm.common.groovy.BaseScript
    import org.commonjava.maven.ext.core.groovy.InvocationPoint
    import org.commonjava.maven.ext.core.groovy.InvocationStage
    import org.apache.commons.lang.StringUtils
    import org.jboss.gm.common.model.ManipulationModel
    import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef

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

### Developing Groovy Scripts

To make it easier to develop scripts for both GME (this project) and [PME](https://github.com/release-engineering/pom-manipulation-ext) an example project has been setup. The [manipulator-groovy-examples](https://github.com/project-ncl/manipulator-groovy-examples) provides a framework to develop and test such scripts.
