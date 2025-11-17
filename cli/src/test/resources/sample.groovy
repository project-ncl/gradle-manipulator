import org.jboss.pnc.mavenmanipulator.core.groovy.GMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.gradlemanipulator.common.groovy.BaseScript
import org.jboss.pnc.gradlemanipulator.common.utils.PluginUtils

@GrabResolver(name='private', root='https://maven.repository.redhat.com/techpreview/all/')
@Grab('org.zeroturnaround:zt-exec:1.10')

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@GMEBaseScript BaseScript gmeScript

println("Running Groovy script on " + gmeScript.getBaseDir())
println("Known properties " + gmeScript.getUserProperties())

println(PluginUtils.getSupportedPlugins())
// Hack to avoid using imports and groovy script causing errors in IntelliJ.
Class c = Class.forName("org.zeroturnaround.exec.ProcessExecutor")
def pExecutor = c.newInstance()
pExecutor.exitValueAny()
System.out.println("Executor " + pExecutor)
