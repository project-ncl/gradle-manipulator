---
title: Groovy Script Injection
---

### Overview

The tool offers the ability to run arbitrary groovy scripts on the sources prior to running the build. This allows GME to be extensible by the user and to carry out non-generic modifications.


### Configuration

If the property `-DgroovyScripts=<value>,....` is set, it will load the remote Groovy script file.

The argument should a comma separated list of HTTP / HTTPS URLs.


### Groovy Scripts

Each groovy script will be run on the execution root (i.e. where Gradle is invoked).


Each script <b>must</b> use the following annotations:

```
import groovy.transform.BaseScript
import org.jboss.gm.analyzer.alignment.groovy.GMEBaseScript

@BaseScript GMEBaseScript gmeScript

```

Note that the scripts will be run **as the last part** of the alignment process.

<br/>
The following API is made available:


| Method | Description |
| -------|:------------|
| [Project](https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html) getProject() | Return the root Project |
| [ManipulationModel](https://github.com/project-ncl/gradle-manipulator/blob/master/common/src/main/java/org/jboss/gm/common/model/ManipulationModel.java) getModel() | Return the current ManipulationModel |


This can then be invoked by e.g.

    gmeScript.getProject()


A typical groovy script that alters a JSON file on disk might be:


    import groovy.transform.BaseScript
    import org.apache.commons.lang.StringUtils
    import org.jboss.gm.analyzer.alignment.groovy.GMEBaseScript
    import org.jboss.gm.common.model.ManipulationModel
    import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef

    @BaseScript GMEBaseScript gmeScript

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
