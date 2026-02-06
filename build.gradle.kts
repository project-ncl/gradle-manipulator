@file:Suppress("UnstableApiUsage")

import com.adarshr.gradle.testlogger.theme.ThemeType
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.freefair.gradle.plugins.lombok.LombokExtension
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.reflect.full.memberFunctions
import net.linguica.gradle.maven.settings.LocalMavenSettingsLoader
import net.linguica.gradle.maven.settings.MavenSettingsPlugin.MAVEN_SETTINGS_EXTENSION_NAME
import net.linguica.gradle.maven.settings.MavenSettingsPluginExtension
import org.ajoberstar.grgit.Grgit
import org.apache.maven.settings.building.SettingsBuildingException
import org.gradle.kotlin.dsl.project

plugins {
    java
    signing
    `maven-publish`
    idea

    // Note spotless is only active for Gradle >= 6.1.1. Using 6.8.3 for the extra fixes.
    if (GradleVersion.current() >= GradleVersion.version("6.8.3")) {
        id("com.diffplug.spotless") version "7.2.1"
    } else if (GradleVersion.current() < GradleVersion.version("5.4")) {
        id("com.diffplug.gradle.spotless") version "4.5.1"
    } else {
        id("com.diffplug.spotless") version "5.14.2"
    }

    if (GradleVersion.current() >= GradleVersion.version("6.0")) {
        id("com.gradle.plugin-publish") version "1.3.1" apply false
    } else {
        id("com.gradle.plugin-publish") version "0.21.0"
    }
    id("net.researchgate.release") version "2.8.1"
    id("org.ajoberstar.grgit") version "4.1.1"
    // Were using mark-vieira/gradle-maven-settings-plugin but due to
    // https://github.com/mark-vieira/gradle-maven-settings-plugin/issues/29 it doesn't work with Gradle >= 8.2
    // We really only need minimal code from it anyway
    id("io.github.rmanibus.maven-settings") version "0.8" apply false

    when {
        GradleVersion.current() < GradleVersion.version("5.0") -> {
            id("com.adarshr.test-logger") version "1.7.1"
            // XXX: Versions 4.x > 4.0.1 suffer from <https://github.com/johnrengelman/shadow/issues/425>
            id("com.github.johnrengelman.shadow") version "4.0.1" apply false
        }
        GradleVersion.current() < GradleVersion.version("6.0") -> {
            id("com.adarshr.test-logger") version "2.1.1"
            id("com.github.johnrengelman.shadow") version "5.2.0" apply false
        }
        GradleVersion.current() < GradleVersion.version("7.0") -> {
            id("com.adarshr.test-logger") version "2.1.1"
            id("com.github.johnrengelman.shadow") version "6.1.0" apply false
        }
        GradleVersion.current() < GradleVersion.version("8.0") -> {
            id("com.adarshr.test-logger") version "3.2.0"
            id("com.github.johnrengelman.shadow") version "7.1.2" apply false
        }
        GradleVersion.current() < GradleVersion.version("8.3") -> {
            id("com.adarshr.test-logger") version "3.2.0"
            id("com.github.johnrengelman.shadow") version "8.1.1" apply false
        }
        GradleVersion.current() >= GradleVersion.version("9.0.0") -> {
            id("com.adarshr.test-logger") version "4.0.0"
            id("com.gradleup.shadow") version "9.0.2" apply false
            id("com.gradleup.nmcp.aggregation") version "1.2.0" apply false
        }
        // For all Gradle 8.x after 8.3 (which is used for release). Note when running with release
        // enabled we were encountering https://github.com/GradleUp/shadow/issues/875
        // so have switched to using 8.3.9 instead of 8.3.6
        else -> {
            id("com.adarshr.test-logger") version "4.0.0"
            id("com.gradleup.shadow") version "8.3.9" apply false
            // TODO: Can't reset wrapper with this so for IntelliJ might need to be commented out :-(
            id("com.gradleup.nmcp.aggregation") version "1.2.0" apply false
        }
    }

    when {
        GradleVersion.current() < GradleVersion.version("5.0") -> {
            id("io.freefair.lombok") version "2.9.5" apply false
        }
        GradleVersion.current() < GradleVersion.version("5.2") -> {
            id("io.freefair.lombok") version "3.0.0" apply false
        }
        GradleVersion.current() < GradleVersion.version("6.0") -> {
            id("io.freefair.lombok") version "4.1.6" apply false
        }
        GradleVersion.current() < GradleVersion.version("8.0") -> {
            id("io.freefair.lombok") version "5.3.3.3" apply false
        }
        else -> {
            id("io.freefair.lombok") version "6.6.3" apply false
        }
    }

    // Not compatible with Gradle 9 yet.
    // https://github.com/kordamp/kordamp-gradle-plugins/issues/540
    // See also below as a fake task for AggregateJacocoReport has been created for Gradle 9
    if (GradleVersion.current() < GradleVersion.version("9.0.0")) {
        if (GradleVersion.current() >= GradleVersion.version("8.0")) {
            id("org.kordamp.gradle.jacoco") version "0.54.0"
        } else if (GradleVersion.current() >= GradleVersion.version("7.0")) {
            id("org.kordamp.gradle.jacoco") version "0.47.0"
        } else if (GradleVersion.current() >= GradleVersion.version("5.3")) {
            id("org.kordamp.gradle.jacoco") version "0.46.0"
        }
    }
}

tasks.withType<Wrapper>().configureEach { distributionType = Wrapper.DistributionType.ALL }

if (!JavaVersion.current().isJava11Compatible) {
    throw GradleException("This build must be run with at least Java 11")
} else if (GradleVersion.current() < GradleVersion.version("4.10")) {
    throw GradleException("This build must be run with at least Gradle 4.10")
}

// XXX: Jacoco plugin only supports Gradle >= 5.3 ; create empty task on those Gradle versions so that build does not
// fail
if (GradleVersion.current() < GradleVersion.version("5.3") ||
    GradleVersion.current() >= GradleVersion.version("9.0.0")) {
    tasks.register("AggregateJacocoReport")
}

apply(plugin = "net.researchgate.release")

release {
    // https://github.com/researchgate/gradle-release/issues/340
    // https://github.com/researchgate/gradle-release/issues/281
    val gitConfig = getProperty("git") as net.researchgate.release.GitAdapter.GitConfig
    gitConfig.requireBranch = "main"
}

// This was tasks.beforeReleaseBuild to hook into the release plugin system, but we are manually handling the task
// ordering
tasks.register("fixupReadme") {
    doLast {
        if ("true" == System.getProperty("release", "") && project == project.rootProject) {
            val tmp = File(System.getProperty("java.io.tmpdir"))
            val source = File(project.rootDir, "README.md")
            val searchString = "https://repo1.maven.org/maven2/org/jboss/pnc/gradle-manipulator/analyzer"

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
                            "$1/${project.version}/analyzer-${project.version}-init.gradle".replace("-SNAPSHOT", ""))
                    } else line
                }
            }
            Files.move(File(tmp, "README.md").toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING)

            val grgit = Grgit.open(mapOf("currentDir" to project.rootDir))
            // Only commit README if there are any changes.
            if (!grgit.status().isClean) {
                logger.info("Committing README update")
                grgit.commit(mapOf("message" to "Committing README Version Changes", "paths" to setOf("README.md")))
                grgit.push()
            }
        }
    }
}

// In
// https://github.com/researchgate/gradle-release/blob/main/src/main/groovy/net/researchgate/release/ReleasePlugin.groovy#L116,
// the list of task interdependencies is specified. However, as per
// https://github.com/researchgate/gradle-release/issues/298,
// we want to ensure the tag is done before the build so the manifest (etc.) points to the correct SHA. As the
// beforeReleaseBuild
// then runs at the wrong point with this change, we manually inject a task (fixupReadme) below.
tasks.getByName("preTagCommit") {
    logger.info("Altering preTagCommit to run after checkSnapshotDependencies instead of runBuildTasks")
    setMustRunAfter(listOf(tasks["checkSnapshotDependencies"]))
    dependsOn("fixupReadme")
}

tasks.getByName("runBuildTasks") {
    logger.info("Altering runBuildTasks to run after createReleaseTag instead of checkSnapshotDependencies")
    setMustRunAfter(listOf(tasks["createReleaseTag"]))
}

tasks.getByName("checkoutMergeFromReleaseBranch") {
    logger.info("Altering checkoutMergeFromReleaseBranch to run after runBuildTasks instead of createReleaseTag")
    setMustRunAfter(listOf(tasks["runBuildTasks"]))
}

tasks.getByName("afterReleaseBuild") {
    dependsOn(
        ":common:publish",
        ":analyzer:publish",
        ":manipulation:publish",
        ":cli:publish",
        ":analyzer:publishPlugins",
        ":manipulation:publishPlugins",
        ":publishToCentral")
}

allprojects {
    extra["gradleReleaseVersion"] = "8.3"

    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases-local/") }
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots") }
        maven { url = uri("https://maven.repository.redhat.com/ga/") }
    }

    if (GradleVersion.current() < GradleVersion.version("5.4")) {
        apply(plugin = "com.diffplug.gradle.spotless")
    } else {
        apply(plugin = "com.diffplug.spotless")
    }

    val spotlessConfig by configurations.creating
    dependencies { spotlessConfig("org.jboss.pnc:ide-config:1.1.0") }

    spotless {
        java {
            // Can't use asFile (from
            // https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.resources/-text-resource/index.html )
            // as that creates and writes the File immediately ... which is then deleted
            // by Gradle clean. configProperties was only available from Spotless 7.0 (which requires Gradle >= 6.1.1)
            if (GradleVersion.current() >= GradleVersion.version("${project.extra.get("gradleReleaseVersion")}")) {
                removeUnusedImports()
                importOrder(resources.text.fromArchiveEntry(spotlessConfig, "java-import-order.txt").asString())
                val formatter = resources.text.fromArchiveEntry(spotlessConfig, "java-formatter.xml").asString()
                // Using reflection instead of 'eclipse().configXml(formatter)' to avoid issues when earlier spotless
                // plugin versions are used.
                val eclipseConfigXml =
                    com.diffplug.gradle.spotless.JavaExtension.EclipseConfig::class.memberFunctions.find {
                        it.name == "configXml"
                    }
                eclipseConfigXml?.call(eclipse(), arrayOf(formatter))
            }
        }
    }
    if (GradleVersion.current() >= GradleVersion.version("${project.extra.get("gradleReleaseVersion")}")) {
        apply { from("$rootDir/gradle/spotless.gradle") }
    }

    tasks.withType<JavaCompile>().configureEach {
        if (GradleVersion.current() >= GradleVersion.version("${project.extra.get("gradleReleaseVersion")}")) {
            dependsOn("spotlessApply")
        }
    }
}

subprojects {
    extra["assertjVersion"] = "3.27.7"
    extra["atlasVersion"] = "1.1.8"
    extra["bytemanVersion"] = "4.0.15"
    extra["commonsBeanVersion"] = "1.11.0"
    extra["commonsLangVersion"] = "3.19.0"
    extra["commonsIOVersion"] = "2.21.0"
    // Used in the CLI. Only limited version range available in repo.gradle.org/ui/native/libs-releases-local
    extra["gradleVersion"] = "5.6.4"
    extra["groovyVersion"] = "3.0.17"
    extra["ivyVersion"] = "2.5.1"
    // Note - this *downgrades* Jackson from what is used in PME. This is due to
    // https://github.com/gradle/gradle/issues/24390
    // https://github.com/FasterXML/jackson-core/issues/955
    // This exclusion isn't required on 7.6.1 and above.
    extra["jacksonVersion"] = "2.14.3"
    // Must use 6.x series as 7.x and above require JDK17
    extra["jgitVersion"] = "6.10.1.202505221210-r"
    extra["junitVersion"] = "4.13.2"
    // Must use 1.3.x series as 1.4.x and above requires JDK11.
    extra["logbackVersion"] = "1.3.16"
    extra["mavenVersion"] = "3.9.12"
    extra["opentelemetryVersion"] = "1.5.0"
    extra["ownerVersion"] = "1.0.12"
    extra["pmeVersion"] = "5.1"
    extra["slf4jVersion"] = "2.0.17"
    extra["systemStubsVersion"] = "2.1.8"

    apply(plugin = "idea")
    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "io.freefair.lombok")

    extra["lombokVersion"] = extensions.findByType(LombokExtension::class)?.version

    // XXX: Lombok plugin 3.x < 3.6.1 suffers from <https://github.com/freefair/gradle-plugins/issues/31>
    if (GradleVersion.current() < GradleVersion.version("5.0") ||
        GradleVersion.current() >= GradleVersion.version("5.2")) {
        // Don't generate lombok.config files (
        // https://docs.freefair.io/gradle-plugins/3.6.6/reference/#_lombok_config_handling )
        tasks.findByName("generateLombokConfig")?.enabled = false
    }

    if (project.name == "common" || project.name == "cli") {
        apply(plugin = "java-library")

        tasks.withType<Jar>().configureEach {
            manifest {
                attributes["Built-By"] = System.getProperty("user.name")
                attributes["Build-Timestamp"] = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date())
                attributes["Scm-Revision"] = Grgit.open(mapOf("currentDir" to project.rootDir)).use { g -> g.head().id }
                attributes["Created-By"] = "Gradle ${gradle.gradleVersion}"
                attributes["Build-Jdk"] =
                    System.getProperty("java.version") +
                        " ; " +
                        System.getProperty("java.vendor") +
                        " ; " +
                        System.getProperty("java.vm.version")
                attributes["Build-OS"] =
                    System.getProperty("os.name") +
                        " ; " +
                        System.getProperty("os.arch") +
                        " ; " +
                        System.getProperty("os.version")
                attributes["Implementation-Version"] = "${project.version}"
            }
        }
    } else {
        // Don't apply Gradle plugin code to the cli tool.
        apply(plugin = "java-gradle-plugin")
        apply(plugin = "com.gradle.plugin-publish")

        tasks.withType<ShadowJar>().configureEach {
            dependencies { exclude(dependency("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")) }
        }
    }
    apply(plugin = "maven-publish")

    val sourcesJar by
        tasks.registering(Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets["main"].allSource)
        }

    val javadocJar by
        tasks.registering(Jar::class) {
            archiveClassifier.set("javadoc")
            from(tasks["javadoc"])
        }

    // XXX: The code below emulates test fixtures as this feature only exists in Gradle 5.6+
    val outputDirectories by configurations.creating {}

    val testFixturesCompile by configurations.creating { extendsFrom(configurations["implementation"]) }

    configurations.create(
        "testFixturesRuntime",
        Action { extendsFrom(configurations["runtimeOnly"], configurations["testFixturesCompile"]) })

    val testFixturesUsageImplementation by
        configurations.creating {
            extendsFrom(configurations["testFixturesCompile"], configurations["outputDirectories"])
        }

    val testFixturesUsageRuntimeOnly by
        configurations.creating {
            extendsFrom(configurations["testFixturesRuntime"], configurations["testFixturesUsageImplementation"])
        }

    configurations["testImplementation"].extendsFrom(testFixturesUsageImplementation)
    configurations["testRuntimeOnly"].extendsFrom(testFixturesUsageRuntimeOnly)

    sourceSets.create(
        "testFixtures",
        Action {
            java.srcDir("src/testFixtures/java")
            resources.srcDir("src/testFixtures/resources")
            compileClasspath = sourceSets["main"].output + configurations["testFixturesCompile"]
            runtimeClasspath = output + compileClasspath + configurations["testFixturesRuntime"]
        })

    dependencies {
        outputDirectories(sourceSets["testFixtures"].output)
        testFixturesUsageImplementation(project(project.path))
        testFixturesCompile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
        testFixturesCompile("junit:junit:${project.extra.get("junitVersion")}")
        testFixturesCompile(gradleApi())
    }

    val testFixturesJar by
        tasks.registering(Jar::class) {
            archiveClassifier.set("test-fixtures")
            from(sourceSets["testFixtures"].output)
        }

    if (project.name == "common") {
        artifacts {
            add("archives", tasks["testFixturesJar"])
            add("testFixturesCompile", testFixturesJar)
        }
    }

    // Avoid guava problems with Android and standard jre selection when updating Maven from 3.9.5 to 3.9.12
    // See https://github.com/google/guava/issues/6612
    // Note: After Gradle 7 org.gradle.api.attributes.java.TargetJvmEnvironment is available.
    configurations.all {
        if (GradleVersion.current() < GradleVersion.version("7.0")) {
            attributes { attribute(Attribute.of("org.gradle.jvm.environment", "".javaClass), "standard-jvm") }
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
        if (GradleVersion.current() < GradleVersion.version("8.3")) {
            apply(plugin = "com.github.johnrengelman.shadow")
        } else {
            apply(plugin = "com.gradleup.shadow")
        }

        // TODO: ### shadow 9 doesn't need this?
        // Make assemble/build task depend on shadowJar
        tasks["assemble"].dependsOn(tasks["shadowJar"])
        // tasks["build"].dependsOn(tasks["shadowJar"])

        tasks.withType<ShadowJar>().configureEach {
            // ensure that a single jar is built which is the shadowed one
            archiveClassifier.set("")
            // no need to add analyzer.init.gradle in the jar since it will never be used from inside the plugin itself
            exclude("analyzer-init.gradle")

            // XXX: Skip minimization for Gradle 4.10 (ShadowJar 4.0.1) due to missing classes
            if (GradleVersion.current() >= GradleVersion.version("5.0")) {
                // Minimise the resulting uber-jars to ensure we don't have massive jars
                minimize {
                    // Sometimes minimisation takes away too much ... ensure we keep these.
                    exclude(dependency("ch.qos.logback:.*:.*"))
                    exclude(dependency("com.fasterxml.jackson.core:.*:.*"))
                    exclude(dependency("com.konghq:.*:.*"))
                    exclude(dependency("io.opentelemetry:.*"))
                    exclude(dependency("org.aeonbits.owner:.*:.*"))
                    exclude(dependency("org.apache.ivy:.*:.*"))
                    exclude(dependency("org.apache.maven:.*:.*"))
                    exclude(dependency("org.commonjava.atlas.maven:.*:.*"))
                    exclude(dependency("org.jboss.pnc.maven-manipulator:.*:.*"))
                    exclude(dependency("org.slf4j:.*:.*"))
                    // Prevent org.jboss.gm.common.groovy.BaseScript from being removed.
                    exclude(project(":common"))
                }
            }

            // We used to relocate the kotlin dependency due to clashes with Gradle 4.x. It's likely that was
            // because of the opentelemetry-java-cli module that had incorrect dependencies due to the Quarkus BOM
            // overriding what OpenTelemtetry requires and bringing in ancient versions of kotlin.
            // relocate("kotlin", "shadow.kotlin")

            doFirst {
                manifest {
                    attributes["Built-By"] = System.getProperty("user.name")
                    attributes["Build-Timestamp"] = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(Date())
                    attributes["Scm-Revision"] =
                        Grgit.open(mapOf("currentDir" to project.rootDir)).use { g -> g.head().id }
                    attributes["Created-By"] = "Gradle ${gradle.gradleVersion}"
                    attributes["Build-Jdk"] =
                        System.getProperty("java.version") +
                            " ; " +
                            System.getProperty("java.vendor") +
                            " ; " +
                            System.getProperty("java.vm.version")
                    attributes["Build-OS"] =
                        System.getProperty("os.name") +
                            " ; " +
                            System.getProperty("os.arch") +
                            " ; " +
                            System.getProperty("os.version")
                    attributes["Implementation-Version"] = "${project.version}"
                }
            }

            isZip64 = true
        }

        // configure publishing of the shadowJar
        var publicationComponent = "shadow"
        if (GradleVersion.current() >= GradleVersion.version("8.3")) {
            if (project.name == "cli") {
                configure<PublishingExtension> {
                    publications {
                        create<MavenPublication>(publicationComponent) {
                            from(components["shadow"])
                            artifact(sourcesJar.get())
                            artifact(javadocJar.get())
                            generatePom()
                        }
                    }
                }
            } else {
                publicationComponent = "pluginMaven"
                // Using afterEvaluate : https://github.com/GradleUp/shadow/issues/1748
                afterEvaluate {
                    configure<PublishingExtension> { publications { withType<MavenPublication> { generatePom() } } }
                }
            }
            apply(plugin = "signing")
            afterEvaluate {
                signing {
                    setRequired({ isReleaseBuild })
                    useGpgCmd()
                    sign(publishing.publications[publicationComponent])
                }
            }
        } else {
            apply { from("$rootDir/gradle/publish.gradle") }
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
        }

        apply(plugin = "signing")
        signing {
            setRequired({ isReleaseBuild })
            useGpgCmd()
            sign(publishing.publications["mavenJava"])
        }
    }

    tasks.withType<Sign> {
        onlyIf { isReleaseBuild }
        // Force ordering to avoid
        // Reason: Task ':analyzer:jar' uses this output of task ':analyzer:signPluginMavenPublication'
        // without declaring an explicit or implicit dependency.
        // dependsOn(tasks["jar"])
        dependsOn(tasks["assemble"])
        mustRunAfter(tasks["test"])
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
        showStandardStreams = true
        showPassedStandardStreams = false
        showSkippedStandardStreams = false
    }

    tasks.withType<Javadoc>().configureEach {
        // https://github.com/gradle/gradle/issues/7038
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
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
            connection.set("scm:git:https://github.com/project-ncl/gradle-manipulator.git")
            developerConnection.set("scm:git:git@github.com:project-ncl/gradle-manipulator.git")
            url.set("https://github.com/project-ncl/gradle-manipulator")
        }
    }
}

fun loadSettings(extension: MavenSettingsPluginExtension, repository: String) {
    val settingsLoader = LocalMavenSettingsLoader(extension)
    try {
        val settings = settingsLoader.loadSettings()
        for (server in settings.servers) {
            logger.debug("Settings parser examining server {}", server.id)
            if (repository == server.id) {
                if (server.username != null && server.password != null) {
                    logger.info("Found valid credentials for publishing")
                    // As the nmcp configuration has been moved to a separate groovy file
                    // instead of returning a Pair<String,String> store the values in the ext.
                    project.ext.set("central_username", server.username)
                    project.ext.set("central_password", server.password)
                    return
                }
            }
        }
    } catch (e: SettingsBuildingException) {
        throw GradleScriptException("Unable to read local Maven settings.", e)
    }
    project.ext.set("central_username", "")
    project.ext.set("central_password", "")
}

val isReleaseBuild = ("true" == gradle.startParameter.projectProperties.getOrDefault("release", ""))

if (isReleaseBuild && GradleVersion.current().version != "${project.extra.get("gradleReleaseVersion")}") {
    throw GradleException("Gradle ${project.extra.get("gradleReleaseVersion")} is required to release this project")
} else if (isReleaseBuild) {
    logger.lifecycle("Running as release build")
}

if (System.getProperty("release") != null) {
    throw GradleException("Pass release=true as a -P parameter")
}

if (GradleVersion.current() >= GradleVersion.version("8.3")) {
    // LinkageError: loader constraint violation from GMEFunctionalTest otherwise
    if (System.getProperty("gmeFunctionalTest") == null) {
        val mavenExtension =
            project.extensions.create(MAVEN_SETTINGS_EXTENSION_NAME, MavenSettingsPluginExtension::class, project)
        // Previously was encoding the repository name into repositories that were attached to the
        // publications. As those aren't needed now due to Central portal upload changes, just
        // hardcode the name here. This should match the name in $HOME/.m2/settings.xml
        loadSettings(mavenExtension, "central")
        // We have to delay applying the plugin and load the extension configuration from a groovy file as:
        // 1. We can't use the plugin on other JDK/Gradle combinations
        // 2. We can't use Kotlin types in external files (https://github.com/gradle/gradle/issues/30878) and we
        //    don't want them eagerly precompiled anyway.
        apply(plugin = "com.gradleup.nmcp.aggregation")
        apply { from("$rootDir/gradle/nmcp.gradle") }
        project.rootProject.tasks.register("publishToCentral") {
            description = "Wrapper task for NMCP to handling publishing snapshots or releases"
            group = PublishingPlugin.PUBLISH_TASK_GROUP
            if (project.version.toString().endsWith("-SNAPSHOT")) {
                dependsOn("publishAggregationToCentralPortalSnapshots")
            } else {
                dependsOn("publishAggregationToCentralPortal")
            }
        }
    }
}
