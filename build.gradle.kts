plugins {
    id("com.diffplug.gradle.spotless") version "3.21.0"
}

allprojects {
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {

    apply(plugin = "maven-publish")
    if (!project.name.equals("common")) {
        apply(plugin = "java-gradle-plugin")
    }
    apply(plugin = "com.diffplug.gradle.spotless")

    spotless {
        java {
            importOrderFile("$rootDir/ide-config/eclipse.importorder")
            eclipse().configFile("$rootDir/ide-config/eclipse-format.xml")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        dependsOn("spotlessApply")
    }
}