plugins {
  id("java-platform")

  id("otel.publish-conventions")
}

description = "OpenTelemetry Instrumentation Bill of Materials (Alpha)"
group = "io.opentelemetry.instrumentation"
base.archivesName.set("opentelemetry-instrumentation-bom-alpha")

javaPlatform {
  allowDependencies()
}

dependencies {
  api(platform("io.opentelemetry:opentelemetry-bom:1.17.0"))
  api(platform("io.opentelemetry:opentelemetry-bom-alpha:1.17.0-alpha"))
}

dependencies {
  constraints {
    rootProject.subprojects {
      val proj = this
      if (!proj.name.startsWith("bom") && proj.name != "javaagent") {
        proj.plugins.withId("maven-publish") {
          api(proj)
        }
      }
    }
  }
}
