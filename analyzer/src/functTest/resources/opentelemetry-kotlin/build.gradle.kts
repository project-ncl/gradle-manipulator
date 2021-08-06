
import com.diffplug.gradle.spotless.SpotlessExtension
import com.google.protobuf.gradle.*
import io.morethan.jmhreport.gradle.JmhReportExtension
import me.champeau.gradle.JMHPluginExtension
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.ErrorPronePlugin
import org.gradle.api.plugins.JavaPlugin.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferPlugin
import java.time.Duration

plugins {
    id("com.diffplug.spotless") version "5.9.0"
    id("com.github.ben-manes.versions") version "0.36.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("com.google.protobuf") version "0.8.14" apply false
    id("de.undercouch.download") version "4.1.1"
    id("io.morethan.jmhreport") version "0.9.0" apply false
    id("me.champeau.gradle.jmh") version "0.5.2" apply false
    id("net.ltgt.errorprone") version "1.3.0" apply false
    id("org.unbroken-dome.test-sets") version "3.0.1"
    id("ru.vyarus.animalsniffer") version "1.5.2" apply false

    id("org.jboss.gm.analyzer")
}


subprojects {
    apply(plugin = "org.jboss.gm.analyzer")
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    group = "io.opentelemetry"
    version = "0.17.0" // CURRENT_OPEN_TELEMETRY_VERSION

    plugins.withId("java") {
        plugins.apply("checkstyle")
        plugins.apply("eclipse")
        plugins.apply("idea")
        plugins.apply("jacoco")

        plugins.apply("com.diffplug.spotless")
        plugins.apply("net.ltgt.errorprone")

        configure<BasePluginConvention> {
            archivesBaseName = "opentelemetry-${name}"
        }

        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        configure<CheckstyleExtension> {
            configDirectory.set(file("$rootDir/buildscripts/"))
            toolVersion = "8.12"
            isIgnoreFailures = false
            configProperties["rootDir"] = rootDir
        }

        configure<JacocoPluginExtension> {
            toolVersion = "0.8.6"
        }

        tasks {
            withType(JavaCompile::class) {

                options.encoding = "UTF-8"

                if (name.contains("Test")) {
                    // serialVersionUID is basically guaranteed to be useless in tests
                    options.compilerArgs.add("-Xlint:-serial")
                }

                (options as ExtensionAware).extensions.configure<ErrorProneOptions> {
                    disableWarningsInGeneratedCode.set(true)
                    allDisabledChecksAsWarnings.set(true)

                    // Doesn't currently use Var annotations.
                    disable("Var") // "-Xep:Var:OFF"

                    // ImmutableRefactoring suggests using com.google.errorprone.annotations.Immutable,
                    // but currently uses javax.annotation.concurrent.Immutable
                    disable("ImmutableRefactoring") // "-Xep:ImmutableRefactoring:OFF"

                    // AutoValueImmutableFields suggests returning Guava types from API methods
                    disable("AutoValueImmutableFields")
                    // "-Xep:AutoValueImmutableFields:OFF"

                    // Fully qualified names may be necessary when deprecating a class to avoid
                    // deprecation warning.
                    disable("UnnecessarilyFullyQualified")

                    // Ignore warnings for protobuf and jmh generated files.
                    excludedPaths.set(".*generated.*")
                    // "-XepExcludedPaths:.*/build/generated/source/proto/.*"

                    disable("Java7ApiChecker")
                    disable("AndroidJdkLibsChecker")
                    //apparently disabling android doesn't disable this
                    disable("StaticOrDefaultInterfaceMethod")

                    //until we have everything converted, we need these
                    disable("JdkObsolete")
                    disable("UnnecessaryAnonymousClass")

                    // Limits APIs
                    disable("NoFunctionalReturnType")

                    // We don't depend on Guava so use normal splitting
                    disable("StringSplitter")

                    // Prevents lazy initialization
                    disable("InitializeInline")

                    if (name.contains("Jmh") || name.contains("Test")) {
                        // Allow underscore in test-type method names
                        disable("MemberName")
                    }
                }
            }

            withType(Test::class) {
                useJUnitPlatform()

                testLogging {
                    exceptionFormat = TestExceptionFormat.FULL
                    showExceptions = true
                    showCauses = true
                    showStackTraces = true
                }
                maxHeapSize = "1500m"
            }

            afterEvaluate {
                withType(Jar::class) {
                    val moduleName: String by project
                    inputs.property("moduleName", moduleName)

                    manifest {
                        attributes("Automatic-Module-Name" to moduleName)
                    }
                }
            }
        }

        // Do not generate reports for individual projects
        tasks.named("jacocoTestReport") {
            enabled = false
        }

        configurations {
            val implementation by getting

            create("transitiveSourceElements") {
                isVisible = false
                isCanBeResolved = false
                isCanBeConsumed = true
                extendsFrom(implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("source-folders"))
                }
                val mainSources = the<JavaPluginConvention>().sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                mainSources.java.srcDirs.forEach {
                    outgoing.artifact(it)
                }
            }

            create("coverageDataElements") {
                isVisible = false
                isCanBeResolved = false
                isCanBeConsumed = true
                extendsFrom(implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("jacoco-coverage-data"))
                }
                // This will cause the test task to run if the coverage data is requested by the aggregation task
                tasks.withType(Test::class) {
                    outgoing.artifact(extensions.getByType<JacocoTaskExtension>().destinationFile!!)
                }
            }

            configureEach {
                resolutionStrategy {
                    failOnVersionConflict()
                    preferProjectModules()
                }
            }
        }

        dependencies {
            configurations.configureEach {
                // Gradle and newer plugins will set these configuration properties correctly.
                if (isCanBeResolved && !isCanBeConsumed
                        // Older ones (like JMH) may not, so check the name as well.
                        // Kotlin compiler classpaths don't support BOM nor need it.
                        || name.endsWith("Classpath") && !name.startsWith("kotlin")) {
                    add(name, platform(project(":dependencyManagement")))
                }
            }

            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "nl.jqno.equalsverifier:equalsverifier")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.mockito:mockito-core")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.mockito:mockito-junit-jupiter")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.assertj:assertj-core")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.awaitility:awaitility")
            add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, "io.github.netmikey.logunit:logunit-jul")

            add(ANNOTATION_PROCESSOR_CONFIGURATION_NAME, "com.google.guava:guava-beta-checker")
        }

        plugins.withId("com.google.protobuf") {
            protobuf {
                val versions: Map<String, String> by project
                protoc {
                    // The artifact spec for the Protobuf Compiler
                    artifact = "com.google.protobuf:protoc:${versions["com.google.protobuf"]}"
                }
                plugins {
                    id("grpc") {
                        artifact = "io.grpc:protoc-gen-grpc-java:${versions["io.grpc"]}"
                    }
                }
                generateProtoTasks {
                    all().configureEach {
                        plugins {
                            id("grpc")
                        }
                    }
                }
            }

            afterEvaluate {
                // Classpath when compiling protos, we add dependency management directly
                // since it doesn't follow Gradle conventions of naming / properties.
                dependencies {
                    add("compileProtoPath", platform(project(":dependencyManagement")))
                    add("testCompileProtoPath", platform(project(":dependencyManagement")))
                }
            }
        }

        plugins.withId("ru.vyarus.animalsniffer") {
            dependencies {
                add(AnimalSnifferPlugin.SIGNATURE_CONF, "com.toasttab.android:gummy-bears-api-24:0.3.0:coreLib@signature")
            }

            configure<AnimalSnifferExtension> {
                sourceSets = listOf(the<JavaPluginConvention>().sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME))
            }
        }

        plugins.withId("me.champeau.gradle.jmh") {
            // Always include the jmhreport plugin and run it after jmh task.
            plugins.apply("io.morethan.jmhreport")
            dependencies {
                add("jmh", "org.openjdk.jmh:jmh-core")
                add("jmh", "org.openjdk.jmh:jmh-generator-bytecode")
            }

            // invoke jmh on a single benchmark class like so:
            //   ./gradlew -PjmhIncludeSingleClass=StatsTraceContextBenchmark clean :grpc-core:jmh
            configure<JMHPluginExtension> {
                failOnError = true
                resultFormat = "JSON"
                // Otherwise an error will happen:
                // Could not expand ZIP 'byte-buddy-agent-1.9.7.jar'.
                isIncludeTests = false
                profilers = listOf("gc")
                val jmhIncludeSingleClass: String? by project
                if (jmhIncludeSingleClass != null) {
                    include = listOf(jmhIncludeSingleClass)
                }
            }

            configure<JmhReportExtension> {
                jmhResultPath = file("${buildDir}/reports/jmh/results.json").absolutePath
                jmhReportOutput = file("${buildDir}/reports/jmh").absolutePath
            }

            tasks {
                named("jmh") {
                    finalizedBy(named("jmhReport"))
                }
            }
        }
    }

    plugins.withId("maven-publish") {
        plugins.apply("signing")

        configure<PublishingExtension> {
            publications {
                register<MavenPublication>("mavenPublication") {
                    val release = findProperty("otel.release")
                    if (release != null) {
                        val versionParts = version.split('-').toMutableList()
                        versionParts[0] += "-${release}"
                        version = versionParts.joinToString("-")
                    }
                    groupId = "io.opentelemetry"
                    afterEvaluate {
                        // not available until evaluated.
                        artifactId = the<BasePluginConvention>().archivesBaseName
                        pom.description.set(project.description)
                    }

                    plugins.withId("java-platform") {
                        from(components["javaPlatform"])
                    }
                    plugins.withId("java-library") {
                        from(components["java"])
                    }

                    versionMapping {
                        allVariants {
                            fromResolutionResult()
                        }
                    }

                    pom {
                        name.set("OpenTelemetry Java")
                        url.set("https://github.com/open-telemetry/opentelemetry-java")

                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }

                        developers {
                            developer {
                                id.set("opentelemetry")
                                name.set("OpenTelemetry")
                                url.set("https://github.com/open-telemetry/community")
                            }
                        }

                        scm {
                            connection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java.git")
                            developerConnection.set("scm:git:git@github.com:open-telemetry/opentelemetry-java.git")
                            url.set("git@github.com:open-telemetry/opentelemetry-java.git")
                        }
                    }
                }
            }
        }

        tasks.withType(Sign::class) {
            onlyIf { System.getenv("CI") != null }
        }

        configure<SigningExtension> {
            useInMemoryPgpKeys(System.getenv("GPG_PRIVATE_KEY"), System.getenv("GPG_PASSWORD"))
            sign(the<PublishingExtension>().publications["mavenPublication"])
        }
    }
}
