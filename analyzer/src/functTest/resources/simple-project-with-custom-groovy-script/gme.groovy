
/**
 * Example groovy file to manipulate a project.
 *
 */

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef
import org.commonjava.maven.ext.core.groovy.GMEBaseScript
import org.commonjava.maven.ext.core.groovy.InvocationPoint
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.commonjava.maven.ext.io.rest.Translator
import org.jboss.gm.common.groovy.BaseScript

// Use BaseScript annotation to set script for evaluating the DSL.
@InvocationPoint(invocationPoint = InvocationStage.LAST)
@GMEBaseScript BaseScript gmeScript

println("Running Groovy script on " + gmeScript.getProject())
println("\tgroovy found new version is " + gmeScript.getModel().getVersion())
println("Project original version is " + gmeScript.getProject().getVersion())

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


Translator translator = gmeScript.getRESTAPI();
ProjectVersionRef pvr = SimpleProjectVersionRef.parse("org.hibernate:hibernate-core:5.3.7.Final")
Map<ProjectVersionRef, String> result = translator.translateVersions(Collections.singletonList(pvr))
gmeScript.getLogger().warn("Alignment result is {}", result)
File target = gmeScript.getProject().getBuildFile()
newContent = target.text.replaceFirst("classpath \"org.hibernate:hibernate-core:5.3.7.Final",
    "classpath \"org.hibernate:hibernate-core:" + result.get(pvr))
newContent = newContent + """

publishing {
  publications {
    nebula {
      artifact("build/distributions/\${project.name}-\${version}.zip")
      }
   }
}"""
target.text = newContent

gmeScript.getLogger().info("Retrieved content with {}", newContent)
