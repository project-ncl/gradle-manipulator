import org.commonjava.maven.ext.core.groovy.GMEBaseScript
import org.commonjava.maven.ext.core.groovy.InvocationPoint
import org.commonjava.maven.ext.core.groovy.InvocationStage
import org.jboss.gm.common.groovy.BaseScript

@GrabResolver(name='private', root='https://maven.repository.redhat.com/techpreview/all/')
@Grab('org.zeroturnaround:zt-exec:1.10')

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@GMEBaseScript BaseScript gmeScript

println("Running Groovy script on " + gmeScript.getBaseDir())
println("Known properties " + gmeScript.getUserProperties())

// Hack to avoid using imports and groovy script causing errors in IntelliJ.
Class c = Class.forName("org.zeroturnaround.exec.ProcessExecutor")
def pExecutor = c.newInstance()
pExecutor.exitValueAny()
