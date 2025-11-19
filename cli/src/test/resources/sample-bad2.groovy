import org.jboss.pnc.mavenmanipulator.core.groovy.GMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.gm.common.groovy.BaseScript

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@GMEBaseScript BaseScript gmeScript

println("Running Groovy script on " + gmeScript.getBaseDir())
println("Model " + gmeScript.getModel())
