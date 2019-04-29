import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.publish.PublishingExtension

plugins {
    id("com.diffplug.gradle.spotless") version "3.21.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

allprojects {
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

subprojects {

    extra["bytemanVersion"] = "4.0.6"
    extra["pmeVersion"] = "3.6"

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

    if (project.name.equals("common")) {
        apply(plugin = "java-library")
    } else {
        apply(plugin = "maven-publish")
        apply(plugin = "java-gradle-plugin")
        apply(plugin = "com.github.johnrengelman.shadow")

        /**
         * The configuration below has been created by reading the documentation at:
         * https://imperceptiblethoughts.com/shadow/plugins/
         *
         * Another great source of information is the configuration of the shadow plugin itself:
         * https://github.com/johnrengelman/shadow/blob/master/build.gradle
         */

        // We need to do this otherwise the shadowJar tasks takes forever since
        // it tries to shadow the entire gradle api
        // Moreover, the gradleApi and groovy dependencies in the plugins themselves
        // have been explicitly declared with the shadow configuration
        configurations.get("compile").dependencies.remove(dependencies.gradleApi())

        // make build task depend on shadowJar
        val build: DefaultTask by tasks
        val shadowJar = tasks["shadowJar"] as ShadowJar
        build.dependsOn(shadowJar)


        tasks.withType<ShadowJar>() {
            // ensure that a single jar is built which is the shadowed one
            classifier = ""
        }

        // configure publishing of the shadowJar
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("shadow") {
                    project.shadow.component(this)
                }
            }
        }
    }
}