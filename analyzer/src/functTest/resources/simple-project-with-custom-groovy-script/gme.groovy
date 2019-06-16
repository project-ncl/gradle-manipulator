
/**
 * Example groovy file to manipulate a project.
 *
 */

import groovy.transform.BaseScript
import org.jboss.gm.analyzer.alignment.groovy.GMEBaseScript

// Use BaseScript annotation to set script for evaluating the DSL.
@BaseScript GMEBaseScript gmeScript

println "Running Groovy script on " + gmeScript.getProject()
println "\tgroovy found new version is " + gmeScript.getModel().getVersion()

String newVersion = gmeScript.getModel().getVersion()
File information = new File(gmeScript.getProject().getRootDir(), "build.gradle")

def newContent = information.text.replaceAll( "(new CustomVersion[(]\\s')(.*)(',\\sproject\\s[)])", "\$1$newVersion\$3")
information.text = newContent

println "New content is " + newContent
