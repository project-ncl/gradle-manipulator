plugins {
    id("java-library")
    id("maven-publish")

    id("me.champeau.gradle.jmh")
    id("org.unbroken-dome.test-sets")
    id("ru.vyarus.animalsniffer")
}

description = "OpenTelemetry Context (Incubator)"
extra["moduleName"] = "io.opentelemetry.context"
base.archivesBaseName = "opentelemetry-context"


dependencies {
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.junit-pioneer:junit-pioneer")
}
