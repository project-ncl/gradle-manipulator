import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.text.SimpleDateFormat
import java.util.*

plugins {
    java
    signing
    `maven-publish`
    id("org.datlowe.maven-publish-auth") version "2.0.2"
    id("com.diffplug.gradle.spotless") version "3.21.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("net.nemerosa.versioning") version "2.8.2"
    id("com.gradle.plugin-publish") version "0.10.1"
    id("net.researchgate.release") version "2.6.0"
}

apply(plugin = "net.researchgate.release")
release {
    failOnUnversionedFiles = false
}

tasks.named("beforeReleaseBuild") {
    dependsOn (
            ":build",
            "common:build",
            "analyzer:build",
            "manipulation:build"
    )
}
tasks.named("afterReleaseBuild") {
    dependsOn ( "publish")
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }
}

subprojects {

    var isReleaseBuild = false

    if ("true" == System.getProperty("release")) {
        isReleaseBuild = true
    }
    extra["bytemanVersion"] = "4.0.6"
    extra["pmeVersion"] = "3.6.1"

    apply(plugin = "com.diffplug.gradle.spotless")
    apply(plugin = "net.nemerosa.versioning")

    spotless {
        java {
            importOrderFile("$rootDir/ide-config/eclipse.importorder")
            eclipse().configFile("$rootDir/ide-config/eclipse-format.xml")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        dependsOn("spotlessApply")
    }

    if (project.name == "common") {
        apply(plugin = "java-library")
    } else {
        apply(plugin = "signing")
        apply(plugin = "maven-publish")
        apply(plugin = "org.datlowe.maven-publish-auth")
        apply(plugin = "java-gradle-plugin")
        apply(plugin = "com.github.johnrengelman.shadow")
        apply(plugin = "com.gradle.plugin-publish")

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

        tasks.withType<ShadowJar> {
            // ensure that a single jar is built which is the shadowed one
            archiveClassifier.set("")
            dependencies {
                exclude(dependency("org.slf4j:slf4j-api:1.7.25"))
            }
            // no need to add analyzer.init.gradle in the jar since it will never be used from inside the plugin itself
            exclude("analyzer.init.gradle")
        }

        val sourcesJar by tasks.registering(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets.main.get().allSource)
        }

        val javadocJar by tasks.registering(Jar::class) {
            archiveClassifier.set("javadoc")
            from(tasks["javadoc"])
        }

        // configure publishing of the shadowJar
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("shadow") {
                    project.shadow.component(this)
                    artifact(sourcesJar.get())
                    artifact(javadocJar.get())

                    // we publish the init gradle file to make it easy for tools that use
                    // the plugin to set it up without having to create their own init gradle file
                    if (project.name == "analyzer") {
                        artifact("${sourceSets.main.get().output.resourcesDir}/analyzer.init.gradle", {
                            extension = "init.gradle"
                        })
                    }


                    pom {
                        name.set("Gradle Manipulation Extension")
                        description.set("A tool to work with ProjectNCL to manipulate Gradle builds.")
                        url.set("https://github.com/project-ncl/gradle-manipulator")
                        packaging = "jar"

                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("geoand")
                                name.set("Georgios Andrianakis")
                                email.set("gandrian@redhat.com")
                            }
                            developer {
                                id.set("rnc")
                                name.set("Nick Cross")
                                email.set("ncross@redhat.com")
                            }
                            developer {
                                id.set("TomasHofman")
                                name.set("Tomas Hofman")
                                email.set("thofman@redhat.com")
                            }
                            developer {
                                id.set("metacosm")
                                name.set("Chris Laprun")
                                email.set("claprun@redhat.com")
                            }
                        }
                        scm {
                            connection.set("scm:git:http://github.com/project-ncl/gradle-manipulator.git")
                            developerConnection.set("scm:git:git@github.com:project-ncl/gradle-manipulator.git")
                            url.set("https://github.com/project-ncl/gradle-manipulator")
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "sonatype-nexus-staging"
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                }
            }
        }

        if (isReleaseBuild) {
            signing {
                useGpgCmd()
                this.sign(publishing.publications["shadow"])
            }
        }
    }

    // Exclude logback from dependency tree/
    configurations {
        "compile" {
            exclude(group="ch.qos.logback", module="logback-classic")
        }
        "compile" {
            exclude(group="ch.qos.logback", module="logback-core")
        }
    }

    tasks {
        "jar"(Jar::class) {
            this.manifest {
                attributes["Built-By"]=System.getProperty("user.name")
                attributes["Build-Timestamp"]=SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date())
                attributes["Scm-Revision"]=versioning.info.commit
                attributes["Created-By"]="Gradle ${gradle.gradleVersion}"
                attributes["Build-Jdk"]=System.getProperty("java.version") + " ; " + System.getProperty("java.vendor") + " ; " + System.getProperty("java.vm.version")
                attributes["Build-OS"]=System.getProperty("os.name") + " ; " + System.getProperty("os.arch") + " ; " + System.getProperty("os.version")
                attributes["Implementation-Version"]="${project.version}"
            }
        }
    }
}
