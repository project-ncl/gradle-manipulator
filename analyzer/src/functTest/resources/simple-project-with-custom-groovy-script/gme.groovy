
/**
 * Example groovy file to manipulate a project.
 *
 */

import groovy.transform.BaseScript
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef
import org.jboss.gm.analyzer.alignment.groovy.GMEBaseScript


// Use BaseScript annotation to set script for evaluating the DSL.
@BaseScript GMEBaseScript gmeScript

println "Running Groovy script on " + gmeScript.getProject()
println("\tgroovy found new version is " + gmeScript.getModel().getVersion())

gmeScript.getModel().setName("newRoot")

final newUndertowVersion = gmeScript.getModel().getAllAlignedDependencies().values().find {it.asProjectRef() == new SimpleProjectRef("io.undertow", "undertow-core")}.getVersionString()

String newVersion = gmeScript.getModel().getVersion()
File information = new File(gmeScript.getProject().getRootDir(), "build.gradle")

def newContent = information.text.replaceAll( "(new CustomVersion[(]\\s')(.*)(',\\sproject\\s[)])", "\$1$newVersion\$3")
information.text = newContent
newContent = information.text.replace('2.0.15.Final', newUndertowVersion)
information.text = newContent

println "New content is " + newContent

information = new File(gmeScript.getProject().getRootDir(), "settings.gradle")
newContent = information.text.replaceAll("addSubProjects.*x-pack'[)][)]", "")
information.text = newContent

println "New content is " + newContent
