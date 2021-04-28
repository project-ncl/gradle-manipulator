plugins {
    id("java-library")
    id("maven-publish")

    id("me.champeau.gradle.jmh")
    id("ru.vyarus.animalsniffer")
}

description = "OpenTelemetry API"
extra["moduleName"] = "io.opentelemetry.api.metrics"

dependencies {
    api(project(":api:all"))

    testImplementation("edu.berkeley.cs.jqf:jqf-fuzz")
}
