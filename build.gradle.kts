import com.adarshr.gradle.testlogger.theme.ThemeType
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.freefair.gradle.plugins.lombok.LombokExtension
import org.ajoberstar.grgit.Grgit
import org.gradle.util.GradleVersion
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    java
    signing
    `maven-publish`
    idea

    if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.4")) {
        id("com.diffplug.gradle.spotless") version "4.5.1"
    } else {
        id("com.diffplug.spotless") version "5.14.2"
    }

    id("com.gradle.plugin-publish") version "0.15.0"
    id("net.linguica.maven-settings") version "0.5"
    id("net.researchgate.release") version "2.8.1"
    id("org.ajoberstar.grgit") version "4.1.0"

    when {
        org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.0") -> {
            id("com.adarshr.test-logger") version "1.7.1"
            // XXX: Versions 4.x > 4.0.1 suffer from <https://github.com/johnrengelman/shadow/issues/425>
            id("com.github.johnrengelman.shadow") version "4.0.1"
        }
        org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("6.0") -> {
            id("com.adarshr.test-logger") version "2.1.1"
            id("com.github.johnrengelman.shadow") version "5.2.0"
        }
        org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("7.0") -> {
            id("com.adarshr.test-logger") version "2.1.1"
            id("com.github.johnrengelman.shadow") version "6.1.0"
        }
        else -> {
            id("com.adarshr.test-logger") version "3.0.0"
            id("com.github.johnrengelman.shadow") version "7.0.0"
        }
    }

    when {
        org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.0") -> {
            id("io.freefair.lombok") version "2.9.5" apply false
        }
        org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.2") -> {
            id("io.freefair.lombok") version "3.0.0" apply false
        }
        org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("6.0") -> {
            id("io.freefair.lombok") version "4.1.6" apply false
        }
        else -> {
            id("io.freefair.lombok") version "5.3.3.3" apply false
        }
    }

    if (org.gradle.util.GradleVersion.current() >= org.gradle.util.GradleVersion.version("5.3")) {
        id("org.kordamp.gradle.jacoco") version "0.46.0"
    }
}

// XXX: Jacoco plugin only supports Gradle 5.3+; create empty task on older Gradle versions so that build does not fail
if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.3")) {
    tasks.register("AggregateJacocoReport")
}

if (!JavaVersion.current().isJava11Compatible) {
    throw GradleException("This build must be run with at least Java 11")
}

if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("4.10")) {
    throw GradleException("This build must be run with at least Gradle 4.10")
}

apply(plugin = "net.researchgate.release")

if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.0")) {
    task<Wrapper>("wrapper") {
        distributionType = Wrapper.DistributionType.ALL
    }
} else {
    tasks.getByName<Wrapper>("wrapper") {
        distributionType = Wrapper.DistributionType.ALL
    }
}

tasks.getByName("afterReleaseBuild") {
    dependsOn(
        ":common:publish", ":analyzer:publish", ":manipulation:publish", ":cli:publish", ":analyzer:publishPlugins",
        ":manipulation:publishPlugins"
    )
}

// This was tasks.beforeReleaseBuild to hook into the release plugin system, but we are manually handling the task
// ordering
tasks.register("fixupReadme") {
    doLast {
        if ("true" == System.getProperty("release","") && project == project.rootProject) {
            val tmp = File(System.getProperty("java.io.tmpdir"))
            val source = File(project.rootDir, "README.md")
            val searchString = "https://repo1.maven.org/maven2/org/jboss/gm/analyzer"

            if (!source.exists() || Files.readAllLines(source.toPath()).none { s -> s.contains(searchString) }) {
                throw GradleException("Unable to find '$searchString' in README.md")
            }

            project.copy {
                from(source)
                into(tmp)
                filter { line: String ->
                    if (line.contains(searchString)) {
                        line.replaceFirst(
                            "($searchString)(.*)".toRegex(),
                            "$1/${project.version}/analyzer-${project.version}-init.gradle".replace("-SNAPSHOT", "")
                        )
                    } else line
                }
            }
            Files.move(File(tmp, "README.md").toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING)

            val grgit = Grgit.open(mapOf("currentDir" to project.rootDir))
            // Only commit README if there are any changes.
            if (!grgit.status().isClean) {
                logger.info ("Committing README update")
                grgit.commit(mapOf("message" to "Committing README Version Changes", "paths" to setOf("README.md")))
                grgit.push()
            }
        }
    }
}

// In https://github.com/researchgate/gradle-release/blob/main/src/main/groovy/net/researchgate/release/ReleasePlugin.groovy#L116,
// the list of task interdependencies is specified. However, as per https://github.com/researchgate/gradle-release/issues/298,
// we want to ensure the tag is done before the build so the manifest (etc.) points to the correct SHA. As the beforeReleaseBuild
// then runs at the wrong point with this change, we manually inject a task (fixupReadme) below.
tasks.getByName("preTagCommit") {
    logger.info("Altering preTagCommit to run after checkSnapshotDependencies instead of runBuildTasks")
    setMustRunAfter(listOf(tasks["checkSnapshotDependencies"]) )
    dependsOn("fixupReadme")
}

tasks.getByName("runBuildTasks") {
    logger.info("Altering runBuildTasks to run after createReleaseTag instead of checkSnapshotDependencies")
    setMustRunAfter(listOf(tasks["createReleaseTag"]) )
}

tasks.getByName("checkoutMergeFromReleaseBranch") {
    logger.info("Altering checkoutMergeFromReleaseBranch to run after runBuildTasks instead of createReleaseTag")
    setMustRunAfter(listOf(tasks["runBuildTasks"]))
}

allprojects {
    repositories {
        maven {
            url = uri("https://maven-central-eu.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://repo.gradle.org/gradle/libs-releases-local/")
        }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven {
            url = uri("https://maven.repository.redhat.com/techpreview/all")
        }
    }
    apply(plugin = "idea")
}

val isReleaseBuild = ("true" == System.getProperty("release", ""))
if (isReleaseBuild && GradleVersion.current().version != "5.6.4") {
    throw GradleException("Gradle 5.6.4 is required to release this project")
}

subprojects {
    extra["assertjVersion"] = "3.19.0"
    extra["atlasVersion"] = "0.17.2"
    extra["bytemanVersion"] = "4.0.15"
    extra["commonsBeanVersion"] = "1.9.4"
    extra["commonsVersion"] = "2.6"
    extra["gradleVersion"] = "5.6.4"
    extra["groovyVersion"] = "3.0.5"
    extra["jacksonVersion"] = "2.11.2"
    extra["junitVersion"] = "4.13.1"
    extra["logbackVersion"] = "1.2.3"
    extra["mavenVersion"] = "3.5.0"
    extra["ownerVersion"] = "1.0.12"
    extra["pmeVersion"] = "4.5"
    extra["slf4jVersion"] = "1.7.30"
    extra["systemRulesVersion"] = "1.19.0"

    if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.4")) {
        apply(plugin = "com.diffplug.gradle.spotless")
    } else {
        apply(plugin = "com.diffplug.spotless")
    }

    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "io.freefair.lombok")

    extra["lombokVersion"] = extensions.findByType(LombokExtension::class)?.version

    // XXX: Lombok plugin 3.x < 3.6.1 suffers from <https://github.com/freefair/gradle-plugins/issues/31>
    if (org.gradle.util.GradleVersion.current() < org.gradle.util.GradleVersion.version("5.0")
        || org.gradle.util.GradleVersion.current() >= org.gradle.util.GradleVersion.version("5.2")) {
        tasks.getByName("generateLombokConfig") {
            // Don't generate lombok.config files ( https://docs.freefair.io/gradle-plugins/3.6.6/reference/#_lombok_config_handling )
            enabled = false
        }
    }

    if (project.name == "common" || project.name == "cli") {
        apply(plugin = "java-library")

        tasks.withType<Jar> {
            manifest {
                attributes["Built-By"] = System.getProperty("user.name")
                attributes["Build-Timestamp"] = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date())
                attributes["Scm-Revision"] = Grgit.open(mapOf("currentDir" to project.rootDir)).use { g -> g.head().id }
                attributes["Created-By"] = "Gradle ${gradle.gradleVersion}"
                attributes["Build-Jdk"] = System.getProperty("java.version") + " ; " + System.getProperty("java.vendor") + " ; " + System.getProperty("java.vm.version")
                attributes["Build-OS"] = System.getProperty("os.name") + " ; " + System.getProperty("os.arch") + " ; " + System.getProperty("os.version")
                attributes["Implementation-Version"] = "${project.version}"
            }
        }
    } else {
        // Don't apply Gradle plugin code to the cli tool.
        apply(plugin = "java-gradle-plugin")
        apply(plugin = "com.gradle.plugin-publish")

        tasks.withType<ShadowJar> {
            dependencies {
                exclude(dependency("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}"))
            }
        }
    }

    apply(plugin = "signing")
    apply(plugin = "maven-publish")
    apply(plugin = "net.linguica.maven-settings")

    val sourcesJar by tasks.registering(Jar::class) {
        classifier = "sources"
        from(sourceSets["main"].allSource)
    }

    val javadocJar by tasks.registering(Jar::class) {
        classifier = "javadoc"
        from(tasks["javadoc"])
    }

    // XXX: The code below emulates test fixtures as this feature only exists in Gradle 5.6+
    val outputDirectories by configurations.creating {

    }

    val testFixturesCompile by configurations.creating {
        extendsFrom(configurations["implementation"])
    }

    configurations.create("testFixturesRuntime") {
        extendsFrom(configurations["runtimeOnly"], configurations["testFixturesCompile"])
    }

    val testFixturesUsageImplementation by configurations.creating {
        extendsFrom(configurations["testFixturesCompile"], configurations["outputDirectories"])
    }

    val testFixturesUsageRuntimeOnly by configurations.creating {
        extendsFrom(configurations["testFixturesRuntime"], configurations["testFixturesUsageImplementation"])
    }

    configurations["testImplementation"].extendsFrom(testFixturesUsageImplementation)
    configurations["testRuntimeOnly"].extendsFrom(testFixturesUsageRuntimeOnly)

    sourceSets.create("testFixtures") {
        java.srcDir("src/testFixtures/java")
        resources.srcDir("src/testFixtures/resources")
        compileClasspath = sourceSets["main"].output + configurations["testFixturesCompile"]
        runtimeClasspath = output + compileClasspath + configurations["testFixturesRuntime"]
    }

    dependencies {
        outputDirectories(sourceSets["testFixtures"].output)
        testFixturesUsageImplementation(project(project.path))
        testFixturesCompile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
        testFixturesCompile("junit:junit:${project.extra.get("junitVersion")}")
        testFixturesCompile(gradleApi())
    }

    val testFixturesJar by tasks.registering(Jar::class) {
        classifier = "test-fixtures"
        from(sourceSets["testFixtures"].output)
    }

    if (project.name == "common") {
        artifacts {
            add("archives", tasks["testFixturesJar"])
            add("testFixturesCompile", testFixturesJar)
        }
    }

    if (project.name != "common") {
        /*
         * The configuration below has been created by reading the documentation at:
         * https://imperceptiblethoughts.com/shadow/plugins/
         *
         * Another great source of information is the configuration of the shadow plugin itself:
         * https://github.com/johnrengelman/shadow/blob/main/build.gradle
         */
        apply(plugin = "com.github.johnrengelman.shadow")

        // Make assemble/build task depend on shadowJar
        tasks["assemble"].dependsOn(tasks["shadowJar"])

        tasks.withType<ShadowJar> {
            // ensure that a single jar is built which is the shadowed one
            // Using non-deprecated archiveClassifier.set doesn't seem to work.
            @Suppress("DEPRECATION")
            classifier = ""
            // no need to add analyzer.init.gradle in the jar since it will never be used from inside the plugin itself
            exclude("analyzer-init.gradle")

            // XXX: Skip minimization for Gradle 4.10 (ShadowJar 4.0.1) due to missing classes
            if (org.gradle.util.GradleVersion.current() >= org.gradle.util.GradleVersion.version("5.0")) {
                // Minimise the resulting uber-jars to ensure we don't have massive jars
                minimize {
                    // Sometimes minimisation takes away too much ... ensure we keep these.
                    exclude(dependency("com.fasterxml.jackson.core:.*:.*"))
                    exclude(dependency("org.commonjava.maven.ext:.*:.*"))
                    exclude(dependency("org.commonjava.maven.atlas:.*:.*"))
                    exclude(dependency("org.aeonbits.owner:.*:.*"))
                    exclude(dependency("org.slf4j:.*:.*"))
                    exclude(dependency("org.apache.maven:.*:.*"))
                    exclude(dependency("com.konghq:.*:.*"))
                }
            }

            doFirst {
                manifest {
                    attributes["Built-By"] = System.getProperty("user.name")
                    attributes["Build-Timestamp"] = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date())
                    attributes["Scm-Revision"] = Grgit.open(mapOf("currentDir" to project.rootDir)).use { g -> g.head().id }
                    attributes["Created-By"] = "Gradle ${gradle.gradleVersion}"
                    attributes["Build-Jdk"] = System.getProperty("java.version") + " ; " + System.getProperty("java.vendor") + " ; " + System.getProperty("java.vm.version")
                    attributes["Build-OS"] = System.getProperty("os.name") + " ; " + System.getProperty("os.arch") + " ; " + System.getProperty("os.version")
                    attributes["Implementation-Version"] = "${project.version}"
                }
            }

            isZip64 = true
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
                        artifact("${sourceSets["main"].output.resourcesDir}/analyzer-init.gradle") {
                            classifier = "init"
                            extension = "gradle"
                        }
                    }

                    generatePom()
                }
            }

            generateRepositories(this, this@Build_gradle)
        }

        if (isReleaseBuild) {
            signing {
                useGpgCmd()
                sign(publishing.publications["shadow"])
            }
        }
    } else {
        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifact(sourcesJar.get())
                    artifact(javadocJar.get())
                    artifact(testFixturesJar.get())
                    generatePom()
                }
            }
            generateRepositories(this, this@Build_gradle)
        }

        if (isReleaseBuild) {
            signing {
                useGpgCmd()
                sign(publishing.publications["mavenJava"])
            }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testlogger {
        // Some of our tests take a _long_ time ; remove spurious warnings.
        slowThreshold = 120000
        // Nicer looking theme than default.
        theme = ThemeType.MOCHA
    }

    spotless {
        java {
            importOrderFile("$rootDir/ide-config/eclipse.importorder")
            eclipse().configFile("$rootDir/ide-config/eclipse-format.xml")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        dependsOn("spotlessApply")
    }

    if (project.name != "cli") {
        // Exclude logback from dependency tree.
        configurations {
            "implementation" {
                exclude(group = "ch.qos.logback", module = "logback-classic")
            }

            "implementation" {
                exclude(group = "ch.qos.logback", module = "logback-core")
            }
        }
    }
}

fun generateRepositories(publishingExtension: PublishingExtension, buildGradle: Build_gradle) {
    publishingExtension.repositories {
        if (buildGradle.isReleaseBuild) {
            maven {
                name = "sonatype-nexus-staging"
                url = project.uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            }
        } else {
            maven {
                name = "sonatype-nexus-snapshots"
                url = project.uri("https://oss.sonatype.org/content/repositories/snapshots")
            }
        }
    }
}

fun MavenPublication.generatePom() {
    pom {
        name.set("Gradle Manipulation Extension")
        description.set("A tool to work with ProjectNCL to manipulate Gradle builds.")
        url.set("https://github.com/project-ncl/gradle-manipulator")
        packaging = "jar"

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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

            developer {
                id.set("dwalluck")
                name.set("David Walluck")
                email.set("dwalluck@redhat.com")
            }
        }

        scm {
            connection.set("scm:git:http://github.com/project-ncl/gradle-manipulator.git")
            developerConnection.set("scm:git:git@github.com:project-ncl/gradle-manipulator.git")
            url.set("https://github.com/project-ncl/gradle-manipulator")
        }
    }
}
