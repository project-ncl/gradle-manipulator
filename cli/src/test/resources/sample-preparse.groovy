import org.jboss.pnc.mavenmanipulator.core.groovy.GMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.gm.common.groovy.BaseScript

@InvocationPoint(invocationPoint = InvocationStage.PREPARSE)
@GMEBaseScript BaseScript gmeScript

println("Running Groovy script at PREPARSE on " + gmeScript.getBaseDir())
println("Known properties " + gmeScript.getUserProperties())
println("InvocationStage " + gmeScript.getInvocationStage())
