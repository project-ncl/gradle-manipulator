@file:Suppress("UnstableApiUsage")

import kotlin.reflect.full.memberFunctions
import org.gradle.plugins.ide.idea.model.IdeaModule

group = "org.jboss.pnc.gradle-manipulator"

gradlePlugin {
    // According to https://plugins.gradle.org/docs/publish-plugin the simplifications in plugin
    // publishing requires
    // Gradle 7.6 or later. Therefore use reflection here.
    if (GradleVersion.current() >= GradleVersion.version("7.6")) {
        var pluginPublishMethod =
            GradlePluginDevelopmentExtension::class.memberFunctions.find { it.name == "getWebsite" }
        @Suppress("UNCHECKED_CAST") var wProperty = pluginPublishMethod?.call(this) as Property<String>
        wProperty.set("https://project-ncl.github.io/gradle-manipulator")
        pluginPublishMethod = GradlePluginDevelopmentExtension::class.memberFunctions.find { it.name == "getVcsUrl" }
        @Suppress("UNCHECKED_CAST")
        wProperty = pluginPublishMethod?.call(this) as Property<String>
        wProperty.set("https://github.com/project-ncl/gradle-manipulator.git")
    }
    plugins {
        create(
            "manipulationPlugin",
            Action {
                description =
                    "Plugin that reads the alignment data from \${project.rootDir}/manipulation.json and " +
                        "configures build and publishing to use those versions"
                id = "org.jboss.pnc.gradle-manipulator.manipulation"
                implementationClass = "org.jboss.pnc.gradlemanipulator.manipulation.ManipulationPlugin"
                displayName = "GME Manipulation Plugin"

                if (GradleVersion.current() >= GradleVersion.version("7.6")) {
                    var getTagsMethod = PluginDeclaration::class.memberFunctions.find { it.name == "getTags" }
                    @Suppress("UNCHECKED_CAST") var sProperty = getTagsMethod?.call(this) as SetProperty<String>
                    sProperty.set(listOf("versions", "manipulation"))
                }
            })
    }
}

dependencies {
    implementation(project(":common"))
    // the shadow configuration is used in order to avoid adding gradle and groovy stuff to the
    // shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    implementation("org.apache.commons:commons-lang3:${project.extra.get("commonsLangVersion")}")
    implementation("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "ch.qos.logback")
    }
    implementation("org.commonjava.atlas:atlas-identities:${project.extra.get("atlasVersion")}") {
        exclude(group = "ch.qos.logback")
    }

    // Owner: Need Java8 dependency which pulls in owner itself.
    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")
    testImplementation("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    testImplementation("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    testImplementation("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    testRuntimeOnly("commons-io:commons-io:${project.extra.get("commonsIOVersion")}")
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:${project.extra.get("jgitVersion")}")
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))

    // GradleAPI in test compile to get access to org.gradle.internal.Pair
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
}

if (GradleVersion.current() >= GradleVersion.version("9.0.0")) {
    // Include a fake Upload purely for compilation purposes.
    sourceSets.getByName("main") { java.srcDir("src/gradle9/java") }
}

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
    // testSources.from(sourceSets["functionalTest"].java.srcDirs).
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
}
