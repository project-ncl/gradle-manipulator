import org.jboss.pnc.mavenmanipulator.core.groovy.GMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.gm.common.groovy.BaseScript

@GrabResolver(name='gradle', root='https://repo.gradle.org/gradle/libs-releases/')
@Grab("org.gradle:gradle-base-services:6.1.1")

import org.gradle.internal.util.PropertiesUtils

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@GMEBaseScript BaseScript gmeScript

println("Running Groovy script on " + gmeScript.getBaseDir())

final String DIST_URL = "distributionUrl"
final String DIST_SHA = "distributionSha256Sum"
File gradleWrapper = new File (gmeScript.getBaseDir(), "gradle/wrapper/gradle-wrapper.properties")
Properties properties = new Properties()

if (gradleWrapper.exists()) {
    InputStream inStream = new FileInputStream(gradleWrapper);
    try {
        properties.load(inStream);
    } finally {
        inStream.close();
    }

    String value = properties.getProperty(DIST_URL)
    if (value.contains("gradle-8.10-") || value.contains("gradle-8.10.1-")) {
        gmeScript.getLogger().warn("Found buggy Gradle 8.10/8.10.1 version")
        properties.setProperty(DIST_URL, "https://services.gradle.org/distributions/gradle-8.10.2-all.zip")
        if (properties.containsKey(DIST_SHA)) {
            properties.setProperty(DIST_SHA, "2ab88d6de2c23e6adae7363ae6e29cbdd2a709e992929b48b6530fd0c7133bd6")
        }
        PropertiesUtils.store(properties, gradleWrapper)
    }
}
