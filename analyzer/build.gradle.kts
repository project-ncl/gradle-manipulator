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

val pmeCore = project.property("pmeCore").toString()
val pmeIO = project.property("pmeIO").toString()
val pmeCommonLite = project.property("pmeCommonLite").toString()
val atlasIdentities = project.property("atlasIdentities").toString()
val mavenArtifact = project.property("mavenArtifact").toString()
val mavenCore = project.property("mavenCore").toString()
val mavenModel = project.property("mavenModel").toString()
val mavenSettingsBuilder = project.property("mavenSettingsBuilder").toString()
val mavenSettings = project.property("mavenSettings").toString()
val commonsLang3 = project.property("commonsLang3").toString()
val commonsIO = project.property("commonsIO").toString()
val commonsBeanutils = project.property("commonsBeanutils").toString()
val ownerJava8 = project.property("ownerJava8").toString()
val junit = project.property("junit").toString()
val systemStubsJunit4 = project.property("systemStubsJunit4").toString()
val assertjCore = project.property("assertjCore").toString()
val bytemanBmunit = project.property("bytemanBmunit").toString()
val mockitoCore = project.property("mockitoCore").toString()
val wiremockJre8 = project.property("wiremockJre8").toString()
val jgit = project.property("jgit").toString()

dependencies {
    implementation(project(":common"))
    // The shadow configuration is used in order to avoid adding gradle and groovy stuff to the
    // shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    implementation(pmeCore) {
        exclude(group = "ch.qos.logback")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    implementation(pmeIO) {
        exclude(group = "ch.qos.logback")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    implementation(pmeCommonLite) {
        exclude(group = "ch.qos.logback")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    implementation(atlasIdentities) {
        exclude(group = "ch.qos.logback")
    }

    runtimeOnly(mavenArtifact)
    runtimeOnly(mavenCore)
    runtimeOnly(mavenModel)

    implementation(mavenSettingsBuilder)
    implementation(mavenSettings)

    implementation(commonsLang3)
    implementation(commonsIO)
    implementation(commonsBeanutils)

    implementation(ownerJava8)

    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))
    testImplementation(gradleTestKit())
    testImplementation(junit)
    testImplementation(systemStubsJunit4)
    testImplementation(assertjCore)
    testImplementation(bytemanBmunit)
    testImplementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
    testImplementation(mockitoCore)
    testImplementation(wiremockJre8)
    testImplementation(jgit)
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
        setClassifier("init")
        setExtension("gradle")
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
