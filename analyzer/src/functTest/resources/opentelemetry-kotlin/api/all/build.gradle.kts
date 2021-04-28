plugins {
    id("java-library")
    id("maven-publish")

    id("me.champeau.gradle.jmh")
    id("ru.vyarus.animalsniffer")
}

description = "OpenTelemetry API"
extra["moduleName"] = "io.opentelemetry.api"
base.archivesBaseName = "opentelemetry-api"

dependencies {
    api(project(":api:context"))

    testImplementation("edu.berkeley.cs.jqf:jqf-fuzz")
}
