@file:Suppress("UnstableApiUsage")

import kotlin.reflect.full.memberFunctions
import org.gradle.plugins.ide.idea.model.IdeaModule

group = "org.jboss.pnc.gradle-manipulator"

// According to https://plugins.gradle.org/docs/publish-plugin the simplifications in plugin
// publishing requires
// Gradle 7.6 or later. Therefore use reflection here.
gradlePlugin {
    if (GradleVersion.current() >= GradleVersion.version("7.6")) {
        var pluginPublishMethod =
            GradlePluginDevelopmentExtension::class.memberFunctions.find { it.name == "getWebsite" }
        @Suppress("UNCHECKED_CAST")
        var wProperty: Property<String> = pluginPublishMethod?.call(this) as Property<String>
        wProperty.set("https://project-ncl.github.io/gradle-manipulator")
        pluginPublishMethod = GradlePluginDevelopmentExtension::class.memberFunctions.find { it.name == "getVcsUrl" }
        @Suppress("UNCHECKED_CAST")
        wProperty = pluginPublishMethod?.call(this) as Property<String>
        wProperty.set("https://github.com/project-ncl/gradle-manipulator.git")
    }

    plugins {
        create(
            "alignmentPlugin",
            Action {
                description = "Plugin that that generates alignment metadata at \${project.rootDir}/manipulation.json"
                id = "org.jboss.pnc.gradle-manipulator.analyzer"
                implementationClass = "org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentPlugin"
                displayName = "GME Alignment Plugin"

                if (GradleVersion.current() >= GradleVersion.version("7.6")) {
                    var getTagsMethod = PluginDeclaration::class.memberFunctions.find { it.name == "getTags" }
                    @Suppress("UNCHECKED_CAST") var sProperty = getTagsMethod?.call(this) as SetProperty<String>
                    sProperty.set(listOf("versions", "alignment"))
                }
            })
    }
}

dependencies {
    implementation(project(":common"))
    // The shadow configuration is used in order to avoid adding gradle and groovy stuff to the
    // shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    implementation("com.fasterxml.jackson.core:jackson-databind:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-core:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "ch.qos.logback")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-io:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "ch.qos.logback")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "ch.qos.logback")
    }

    implementation("org.commonjava.atlas:atlas-identities:${project.extra.get("atlasVersion")}") {
        exclude(group = "ch.qos.logback")
    }

    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")

    implementation("org.apache.maven:maven-settings-builder:${project.extra.get("mavenVersion")}")
    implementation("org.apache.maven:maven-settings:${project.extra.get("mavenVersion")}")

    implementation("org.apache.commons:commons-lang3:${project.extra.get("commonsLangVersion")}")
    implementation("commons-io:commons-io:${project.extra.get("commonsIOVersion")}")
    implementation("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    testRuntimeOnly("commons-io:commons-io:${project.extra.get("commonsIOVersion")}")
    testImplementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("org.jboss.byteman:byteman-bmunit:${project.extra.get("bytemanVersion")}")
    testImplementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
    testImplementation("org.mockito:mockito-core:2.27.0")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.26.3")
    testImplementation(gradleKotlinDsl())
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:${project.extra.get("jgitVersion")}")
    testImplementation("pl.pragmatists:JUnitParams:1.1.1")
}

tasks.withType<Test>().configureEach { systemProperties["jdk.attach.allowAttachSelf"] = "true" }

// Separate source set and task for functional tests
val functionalTestSourceSet =
    sourceSets.create(
        "functionalTest",
        Action {
            java.srcDir("src/functTest/java")
            resources.srcDir("src/functTest/resources")
            compileClasspath += sourceSets["main"].output
            runtimeClasspath += output + compileClasspath
        })

configurations.getByName("functionalTestImplementation") { extendsFrom(configurations["testImplementation"]) }

configurations.getByName("functionalTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }

// Previously had to force the addition of the plugin-under-test-metadata.properties but this seems
// to solve it.
gradlePlugin.testSourceSets(functionalTestSourceSet)

idea.module {
    // testSources / testResources only available from 7.4 and greater so can't just do:
    // testSources.from(sourceSets["functionalTest"].java.srcDirs)
    // Not bothering to handle other versions as we're developing on later Gradle now.
    if (GradleVersion.current() >= GradleVersion.version("7.4")) {
        var rTestSources = IdeaModule::class.memberFunctions.find { it.name == "getTestSources" }
        var fileCollection = rTestSources?.call(this) as ConfigurableFileCollection
        fileCollection.from(sourceSets["functionalTest"].java.srcDirs)
        var rTestResources = IdeaModule::class.memberFunctions.find { it.name == "getTestResources" }
        fileCollection = rTestResources?.call(this) as ConfigurableFileCollection
        fileCollection.from(sourceSets["functionalTest"].resources.srcDirs)
    }
}

tasks.register<Test>("functionalTest") {
    description = "Runs functional tests"
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    mustRunAfter(tasks["test"])
    // This will be used in the Wiremock tests - the port needs to match what Wiremock is set up to
    // use
    environment("DA_ENDPOINT_URL", "http://localhost:8089/da/rest/v-1")
    systemProperties["jdk.attach.allowAttachSelf"] = "true"
}

val testJar by
    tasks.registering(Jar::class) {
        mustRunAfter(tasks["functionalTest"])
        archiveClassifier.set("tests")
        from(sourceSets["functionalTest"].output)
        from(sourceSets["test"].output)
    }

// Publish test source jar so it can be reused by manipulator-groovy-examples.
val testSourcesJar by
    tasks.registering(Jar::class) {
        archiveClassifier.set("test-sources")
        from(sourceSets["test"].allSource)
        from(sourceSets["functionalTest"].java.srcDirs)
    }

tasks {
    // This is done in order to use the proper version in the init gradle files
    "processResources"(ProcessResources::class) {
        filesMatching("gme.gradle") { expand(project.properties) }
        filesMatching("analyzer-init.gradle") { expand(project.properties) }
    }
}

// We publish the init gradle file to make it easy for tools that use the plugin to set it up
// without having to create their own init gradle file.
val analyzerFile = layout.buildDirectory.file("resources/main/analyzer-init.gradle")
val prepareAnalyzerInit =
    artifacts.add("default", analyzerFile.get().asFile) {
        classifier = "init"
        extension = "gradle"
        builtBy("processResources")
    }

// Using afterEvaluate : https://github.com/GradleUp/shadow/issues/1748
afterEvaluate {
    configure<PublishingExtension> {
        publications {
            getByName<MavenPublication>("pluginMaven") {
                artifact(testJar.get())
                artifact(testSourcesJar.get())
                artifact(prepareAnalyzerInit)
            }
        }
    }
}
