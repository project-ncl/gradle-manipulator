

/*
 * Copyright (c) 2019, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

import com.github.vlsi.gradle.crlf.CrLfSpec
import com.github.vlsi.gradle.crlf.LineEndings
import com.github.vlsi.gradle.dsl.configureEach
import com.github.vlsi.gradle.git.FindGitAttributes
import com.github.vlsi.gradle.properties.dsl.props
import com.github.vlsi.gradle.properties.dsl.stringProperty
import com.github.vlsi.gradle.publishing.dsl.simplifyXml
import com.github.vlsi.gradle.publishing.dsl.versionFromResolution

plugins {
    // including this plugin directly instead of by an init script, which allows to use the freshly build version
    id("org.jboss.gm.manipulation")
    publishing
    // Verification
    checkstyle
    jacoco
    id("org.owasp.dependencycheck") version "5.3.0"
    id("org.checkerframework") version "0.5.5" apply false
    id("com.github.johnrengelman.shadow") version "5.1.0" apply false
    // IDE configuration
    id("org.jetbrains.gradle.plugin.idea-ext") version "0.7"
    id("com.github.vlsi.ide") version "1.70"
    // Release
    id("com.github.vlsi.crlf") version "1.70"
    id("com.github.vlsi.gradle-extensions") version "1.70"
    id("com.github.vlsi.license-gather") version "1.70" apply false
}

allprojects {
    apply(plugin="org.jboss.gm.manipulation")
}

fun reportsForHumans() = !(System.getenv()["CI"]?.toBoolean() ?: props.bool("CI"))

val lastEditYear = 2020

val enableCheckerframework by props()
val skipCheckstyle by props()
val skipAutostyle by props()
val skipJavadoc by props()
val enableMavenLocal by props()
val enableGradleMetadata by props()
// For instance -PincludeTestTags=!org.postgresql.test.SlowTests
//           or -PincludeTestTags=!org.postgresql.test.Replication
val includeTestTags by props("")
// By default use Java implementation to sign artifacts
// When useGpgCmd=true, then gpg command line tool is used for signing
val useGpgCmd by props()
val slowSuiteLogThreshold = stringProperty("slowSuiteLogThreshold")?.toLong() ?: 0
val slowTestLogThreshold = stringProperty("slowTestLogThreshold")?.toLong() ?: 2000
val jacocoEnabled by extra {
    props.bool("coverage") || gradle.startParameter.taskNames.any { it.contains("jacoco") }
}

ide {
    ideaInstructionsUri =
        uri("https://github.com/pgjdbc/pgjdbc")
    doNotDetectFrameworks("android", "jruby")
}

// This task scans the project for gitignore / gitattributes, and that is reused for building
// source/binary artifacts with the appropriate eol/executable file flags
// It enables to automatically exclude patterns from .gitignore
val gitProps by tasks.registering(FindGitAttributes::class) {
    // Scanning for .gitignore and .gitattributes files in a task avoids doing that
    // when distribution build is not required (e.g. code is just compiled)
    root.set(rootDir)
}

val String.v: String get() = rootProject.extra["$this.version"] as String

val buildVersion = "pgjdbc".v// + releaseParams.snapshotSuffix

println("Building pgjdbc $buildVersion")

val isReleaseVersion = false // rootProject.releaseParams.release.get()

// Configures URLs to SVN and Nexus

val licenseHeaderFile = file("config/license.header.java")

val jacocoReport by tasks.registering(JacocoReport::class) {
    group = "Coverage reports"
    description = "Generates an aggregate report from all subprojects"
}


allprojects {
    group = "org.postgresql"
    version = buildVersion

    apply(plugin = "com.github.vlsi.gradle-extensions")

    repositories {
        if (enableMavenLocal) {
            mavenLocal()
        }
        mavenCentral()
    }

    val javaMainUsed = file("src/main/java").isDirectory
    val javaTestUsed = file("src/test/java").isDirectory
    val javaUsed = javaMainUsed || javaTestUsed
    if (javaUsed) {
        apply(plugin = "java-library")
        if (jacocoEnabled) {
            apply(plugin = "jacoco")
        }
    }

    plugins.withId("java-library") {
        dependencies {
            "implementation"(platform(project(":bom")))
        }
    }

    val kotlinMainUsed = file("src/main/kotlin").isDirectory
    val kotlinTestUsed = file("src/test/kotlin").isDirectory
    val kotlinUsed = kotlinMainUsed || kotlinTestUsed
    if (kotlinUsed) {
        apply(plugin = "java-library")
        apply(plugin = "org.jetbrains.kotlin.jvm")
        dependencies {
            add(if (kotlinMainUsed) "implementation" else "testImplementation", kotlin("stdlib"))
        }
    }

    val hasTests = javaTestUsed || kotlinTestUsed
    if (hasTests) {
        // Add default tests dependencies
        dependencies {
            val testImplementation by configurations
            val testRuntimeOnly by configurations
            testImplementation("org.junit.jupiter:junit-jupiter-api")
            testImplementation("org.junit.jupiter:junit-jupiter-params")
            testImplementation("org.hamcrest:hamcrest")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
            if (project.props.bool("junit4", default = true)) {
                // Allow projects to opt-out of junit dependency, so they can be JUnit5-only
                testImplementation("junit:junit")
                testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
            }
        }
    }

    val skipCheckstyle = skipCheckstyle || props.bool("skipCheckstyle")
    if (!skipCheckstyle) {
        apply<CheckstylePlugin>()
        dependencies {
            checkstyle("com.puppycrawl.tools:checkstyle:${"checkstyle".v}")
        }
        checkstyle {
            // Current one is ~8.8
            // https://github.com/julianhyde/toolbox/issues/3
            isShowViolations = true
            // TOOD: move to /config
            configDirectory.set(File(rootDir, "pgjdbc/src/main/checkstyle"))
            configFile = configDirectory.get().file("checks.xml").asFile
        }
        tasks.register("checkstyleAll") {
            dependsOn(tasks.withType<Checkstyle>())
        }
    }
    if (!skipAutostyle || !skipCheckstyle) {
        tasks.register("style") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Formats code (license header, import order, whitespace at end of line, ...) and executes Checkstyle verifications"
            if (!skipCheckstyle) {
                dependsOn("checkstyleAll")
            }
        }
    }

    tasks.configureEach<AbstractArchiveTask> {
        // Ensure builds are reproducible
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        dirMode = "775".toInt(8)
        fileMode = "664".toInt(8)
    }


    plugins.withType<JacocoPlugin> {
        the<JacocoPluginExtension>().toolVersion = "jacoco".v

        val testTasks = tasks.withType<Test>()
        val javaExecTasks = tasks.withType<JavaExec>()
        // This configuration must be postponed since JacocoTaskExtension might be added inside
        // configure block of a task (== before this code is run). See :src:dist-check:createBatchTask
        afterEvaluate {
            for (t in arrayOf(testTasks, javaExecTasks)) {
                t.configureEach {
                    extensions.findByType<JacocoTaskExtension>()?.apply {
                        // Do not collect coverage when not asked (e.g. via jacocoReport or -Pcoverage)
                        isEnabled = jacocoEnabled
                        // We don't want to collect coverage for third-party classes
                        includes?.add("org.postgresql.*")
                    }
                }
            }
        }

        jacocoReport {
            if (this@allprojects.path.startsWith(":postgresql-jre")) {
                // Caused by: java.lang.IllegalStateException: Can't add different class with same name: org/postgresql/geometric/PGpath
                // Ignore coverage results from Java 1.7 so far
                return@jacocoReport
            }
            // Note: this creates a lazy collection
            // Some of the projects might fail to create a file (e.g. no tests or no coverage),
            // So we check for file existence. Otherwise JacocoMerge would fail
            val execFiles =
                    files(testTasks, javaExecTasks).filter { it.exists() && it.name.endsWith(".exec") }
            executionData(execFiles)
        }

        tasks.configureEach<JacocoReport> {
            reports {
                html.isEnabled = reportsForHumans()
                xml.isEnabled = !reportsForHumans()
            }
        }
    }

    tasks {

        // <editor-fold defaultstate="collapsed" desc="Javadoc configuration">
        configureEach<Javadoc> {
            (options as StandardJavadocDocletOptions).apply {
                // Please refrain from using non-ASCII chars below since the options are passed as
                // javadoc.options file which is parsed with "default encoding"
                noTimestamp.value = true
                showFromProtected()
                if (props.bool("failOnJavadocWarning", default = true)) {
                    // See JDK-8200363 (https://bugs.openjdk.java.net/browse/JDK-8200363)
                    // for information about the -Xwerror option.
                    addBooleanOption("Xwerror", true)
                }
                // javadoc: error - The code being documented uses modules but the packages
                // defined in https://docs.oracle.com/javase/9/docs/api/ are in the unnamed module
                source = "1.8"
                docEncoding = "UTF-8"
                charSet = "UTF-8"
                encoding = "UTF-8"
                docTitle = "PostgreSQL JDBC ${project.name} API"
                windowTitle = "PostgreSQL JDBC ${project.name} API"
                header = "<b>PostgreSQL JDBC</b>"
                bottom =
                    "Copyright &copy; 1997-$lastEditYear PostgreSQL Global Development Group. All Rights Reserved."
                if (JavaVersion.current() >= JavaVersion.VERSION_1_9) {
                    addBooleanOption("html5", true)
                    links("https://docs.oracle.com/javase/9/docs/api/")
                } else {
                    links("https://docs.oracle.com/javase/8/docs/api/")
                }
            }
        }
        // </editor-fold>
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginConvention> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        configure<JavaPluginExtension> {
            // withSourcesJar()
            // if (!skipJavadoc) {
            //     withJavadocJar()
            // }
        }

        val sourceSets: SourceSetContainer by project

        apply(plugin = "maven-publish")

        if (!enableGradleMetadata) {
            tasks.withType<GenerateModuleMetadata> {
                enabled = false
            }
        }

        if (!project.path.startsWith(":postgresql-jre")) {
            if (enableCheckerframework) {
                apply(plugin = "org.checkerframework")
                dependencies {
                    "checkerFramework"("org.checkerframework:checker:${"checkerframework".v}")
                    // CheckerFramework annotations might be used in the code as follows:
                    // dependencies {
                    //     "compileOnly"("org.checkerframework:checker-qual")
                    //     "testCompileOnly"("org.checkerframework:checker-qual")
                    // }
                    if (JavaVersion.current() == JavaVersion.VERSION_1_8) {
                        // only needed for JDK 8
                        "checkerFrameworkAnnotatedJDK"("org.checkerframework:jdk8:${"checkerframework".v}")
                    }
                }
                configure<org.checkerframework.gradle.plugin.CheckerFrameworkExtension> {
                    skipVersionCheck = true
                    excludeTests = true
                    // See https://checkerframework.org/manual/#introduction
                    checkers.add("org.checkerframework.checker.nullness.NullnessChecker")
                    checkers.add("org.checkerframework.checker.optional.OptionalChecker")
                    // checkers.add("org.checkerframework.checker.index.IndexChecker")
                    checkers.add("org.checkerframework.checker.regex.RegexChecker")
                    extraJavacArgs.add("-Astubs=" +
                            fileTree("$rootDir/config/checkerframework") {
                                include("*.astub")
                            }.asPath
                    )
                    // Translation classes are autogenerated, and they
                    extraJavacArgs.add("-AskipDefs=^org\\.postgresql\\.translation\\.")
                    // The below produces too many warnings :(
                    // extraJavacArgs.add("-Alint=redundantNullComparison")
                }
            }
        }

        if (jacocoEnabled && !project.path.startsWith(":postgresql-jre")) {
            // Add each project to combined report
            val mainCode = sourceSets["main"]
            jacocoReport.configure {
                additionalSourceDirs.from(mainCode.allJava.srcDirs)
                sourceDirectories.from(mainCode.allSource.srcDirs)
                classDirectories.from(mainCode.output)
            }
        }

        (sourceSets) {
            "main" {
                resources {
                    exclude("src/main/resources/META-INF/LICENSE")
                }
            }
        }

        tasks {
            configureEach<Jar> {
                manifest {
                    attributes["Bundle-License"] = "BSD-2-Clause"
                    attributes["Implementation-Title"] = "PostgreSQL JDBC Driver"
                    attributes["Implementation-Version"] = project.version
                    val jdbcSpec = props.string("jdbc.specification.version")
                    if (jdbcSpec.isNotBlank()) {
                        attributes["Specification-Vendor"] = "Oracle Corporation"
                        attributes["Specification-Version"] = jdbcSpec
                        attributes["Specification-Title"] = "JDBC"
                    }
                    attributes["Implementation-Vendor"] = "PostgreSQL Global Development Group"
                    attributes["Implementation-Vendor-Id"] = "org.postgresql"
                }
            }

            configureEach<JavaCompile> {
                options.encoding = "UTF-8"
            }
            configureEach<Test> {
                useJUnitPlatform {
                    if (includeTestTags.isNotBlank()) {
                        includeTags.add(includeTestTags)
                    }
                }
                testLogging {
                    showStandardStreams = true
                }
                exclude("**/*Suite*")
                jvmArgs("-Xmx1536m")
                jvmArgs("-Djdk.net.URLClassPath.disableClassPathURLCheck=true")
                // Pass the property to tests
                fun passProperty(name: String, default: String? = null) {
                    val value = System.getProperty(name) ?: default
                    value?.let { systemProperty(name, it) }
                }
                passProperty("preferQueryMode")
                passProperty("java.awt.headless")
                passProperty("junit.jupiter.execution.parallel.enabled", "true")
                passProperty("junit.jupiter.execution.timeout.default", "5 m")
                passProperty("user.language", "TR")
                passProperty("user.country", "tr")
                val props = System.getProperties()
                for (e in props.propertyNames() as `java.util`.Enumeration<String>) {
                    if (e.startsWith("pgjdbc.")) {
                        passProperty(e)
                    }
                }
                for (p in listOf("server", "port", "database", "username", "password",
                        "privilegedUser", "privilegedPassword",
                        "simpleProtocolOnly", "enable_ssl_tests")) {
                    passProperty(p)
                }
            }

            afterEvaluate {
                // Add default license/notice when missing
                configureEach<Jar> {
                    CrLfSpec(LineEndings.LF).run {
                        into("META-INF") {
                            filteringCharset = "UTF-8"
                            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                            // Note: we need "generic Apache-2.0" text without third-party items
                            // So we use the text from $rootDir/config/ since source distribution
                            // contains altered text at $rootDir/LICENSE
                            textFrom("$rootDir/src/main/config/licenses/LICENSE")
                            textFrom("$rootDir/NOTICE")
                        }
                    }
                }
            }
        }

        configure<PublishingExtension> {
//            if (!project.props.bool("nexus.publish", default = true)) {
//                // Some of the artifacts do not need to be published
//                return@configure
//            }

            publications {
                // <editor-fold defaultstate="collapsed" desc="Override published artifacts (e.g. shaded instead of regular)">
                val extraMavenPublications by configurations.creating {
                    isVisible = false
                    isCanBeResolved = false
                    isCanBeConsumed = false
                }
                afterEvaluate {
                    named<MavenPublication>(project.name) {
                        extraMavenPublications.outgoing.artifacts.apply {
                            val keys = mapTo(HashSet()) {
                                it.classifier.orEmpty() to it.extension
                            }
                            artifacts.removeIf {
                                keys.contains(it.classifier.orEmpty() to it.extension)
                            }
                            forEach { artifact(it) }
                        }
                    }
                }
                // </editor-fold>
                // <editor-fold defaultstate="collapsed" desc="Configuration of the published pom.xml">
                create<MavenPublication>(project.name) {
                    artifactId = project.name
                    version = rootProject.version.toString()
                    from(components["java"])

                    // Gradle feature variants can't be mapped to Maven's pom
                    // suppressAllPomMetadataWarnings()

                    // Use the resolved versions in pom.xml
                    // Gradle might have different resolution rules, so we set the versions
                    // that were used in Gradle build/test.
                    versionFromResolution()
                    pom {
                        simplifyXml()
                        name.set(
                            (project.findProperty("artifact.name") as? String) ?: "pgdjbc ${project.name.capitalize()}"
                        )
                        description.set(project.description ?: "PostgreSQL JDBC Driver ${project.name.capitalize()}")
                        inceptionYear.set("1997")
                        url.set("https://jdbc.postgresql.org")
                        licenses {
                            license {
                                name.set("BSD-2-Clause")
                                url.set("https://jdbc.postgresql.org/about/license.html")
                                comments.set("BSD-2-Clause, copyright PostgreSQL Global Development Group")
                                distribution.set("repo")
                            }
                        }
                        organization {
                            name.set("PostgreSQL Global Development Group")
                            url.set("https://jdbc.postgresql.org/")
                        }
                        developers {
                            developer {
                                id.set("davecramer")
                                name.set("Dave Cramer")
                            }
                            developer {
                                id.set("jurka")
                                name.set("Kris Jurka")
                            }
                            developer {
                                id.set("oliver")
                                name.set("Oliver Jowett")
                            }
                            developer {
                                id.set("ringerc")
                                name.set("Craig Ringer")
                            }
                            developer {
                                id.set("vlsi")
                                name.set("Vladimir Sitnikov")
                            }
                            developer {
                                id.set("bokken")
                                name.set("Brett Okken")
                            }
                        }
                        issueManagement {
                            system.set("GitHub issues")
                            url.set("https://github.com/pgjdbc/pgjdbc/issues")
                        }
                        mailingLists {
                            mailingList {
                                name.set("PostgreSQL JDBC development list")
                                subscribe.set("https://lists.postgresql.org/")
                                unsubscribe.set("https://lists.postgresql.org/unsubscribe/")
                                post.set("pgsql-jdbc@postgresql.org")
                                archive.set("https://www.postgresql.org/list/pgsql-jdbc/")
                            }
                        }
                        scm {
                            connection.set("scm:git:https://github.com/pgjdbc/pgjdbc.git")
                            developerConnection.set("scm:git:https://github.com/pgjdbc/pgjdbc.git")
                            url.set("https://github.com/pgjdbc/pgjdbc")
                            tag.set("HEAD")
                        }
                    }
                }
                // </editor-fold>
            }
        }
    }
}

subprojects {
    if (project.path.startsWith(":postgresql")) {
        plugins.withId("java") {
            configure<JavaPluginExtension> {
                val sourceSets: SourceSetContainer by project
                registerFeature("sspi") {
                    usingSourceSet(sourceSets["main"])
                }
                registerFeature("osgi") {
                    usingSourceSet(sourceSets["main"])
                }
            }
        }
    }
}
