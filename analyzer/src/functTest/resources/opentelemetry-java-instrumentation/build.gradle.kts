import java.time.Duration

plugins {
  id("idea")

  id("com.github.ben-manes.versions")
//  id("otel.spotless-conventions")

  id("org.jboss.pnc.gradle-manipulator.analyzer")
}

apply(from = "version.gradle.kts")

description = "OpenTelemetry instrumentations for Java"

subprojects {
    apply(plugin = "org.jboss.pnc.gradle-manipulator.analyzer")
}
