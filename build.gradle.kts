plugins {
    id("com.diffplug.gradle.spotless") version "3.21.0"
    idea
}

allprojects {
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {

    apply(plugin = "maven-publish")
    apply(plugin = "java-gradle-plugin")
    apply(plugin = "com.diffplug.gradle.spotless")
    apply(plugin = "idea")

    spotless {
        java {
            importOrderFile("$rootDir/ide-config/eclipse.importorder")
            eclipse().configFile("$rootDir/ide-config/eclipse-format.xml")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        dependsOn("spotlessApply")
    }

    idea {
        module {
            setDownloadSources(true)
        }
    }
}