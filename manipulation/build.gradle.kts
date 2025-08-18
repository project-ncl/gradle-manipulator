group = "org.jboss.gm"

pluginBundle {
    website = "https://project-ncl.github.io/gradle-manipulator/"
    vcsUrl = "https://github.com/project-ncl/gradle-manipulator/tree/main/manipulation/tree/main/analyzer"
    tags = listOf("versions", "manipulation")
}

gradlePlugin {
    plugins {
        create("manipulationPlugin") {
            description = "Plugin that reads the alignment data from \${project.rootDir}/manipulation.json and " +
                "configures build and publishing to use those versions"
            id = "org.jboss.gm.manipulation"
            implementationClass = "org.jboss.gm.manipulation.ManipulationPlugin"
            displayName = "GME Manipulation Plugin"
        }
    }

    // Disable creation of the plugin marker pom.
    this.isAutomatedPublishing = false
}

dependencies {
    implementation(project(":common"))
    // the shadow configuration is used in order to avoid adding gradle and groovy stuff to the shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    implementation("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    implementation("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    implementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        }
    implementation("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    // Owner: Need Java8 dependency which pulls in owner itself.
    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")
    testImplementation("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    testImplementation("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    testImplementation("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    testRuntimeOnly("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:${project.extra.get("jgitVersion")}")
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))

    // GradleAPI in test compile to get access to org.gradle.internal.Pair
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
}

// Separate source set and task for functional tests
val functionalTestSourceSet = sourceSets.create("functionalTest") {
    java.srcDir("src/functTest/java")
    resources.srcDir("src/functTest/resources")
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += output + compileClasspath
}

configurations.getByName("functionalTestImplementation") {
    extendsFrom(configurations["testImplementation"])
}

configurations.getByName("functionalTestRuntimeOnly") {
    extendsFrom(configurations["testRuntimeOnly"])
}

// Previously had to force the addition of the plugin-under-test-metadata.properties but this seems to solve it.
gradlePlugin.testSourceSets(functionalTestSourceSet)

idea.module {
    val testSources = testSourceDirs
    testSources.addAll(project.sourceSets.getByName("functionalTest").java.srcDirs)
    val testResources = testResourceDirs
    testResources.addAll(project.sourceSets.getByName("functionalTest").resources.srcDirs)
    testSourceDirs = testSources
    testResourceDirs = testResources
}

val functionalTest = task<Test>("functionalTest") {
    description = "Runs functional tests"
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    mustRunAfter(tasks["test"])
}

// Implicit dependencies detected by Gradle 7
// See <https://docs.gradle.org/7.0/userguide/validation_problems.html#implicit_dependency>
tasks.getByName("check") {
    dependsOn(functionalTest)
}

tasks.configureEach {
    if (name == "publishPluginJar") {
        dependsOn("spotlessJava")
    }
}

tasks.getByName("delombok") {
    dependsOn("spotlessJava")
}

tasks.getByName("test") {
    dependsOn("shadowJar")
}

tasks.getByName("functionalTest") {
    dependsOn("shadowJar")
}

tasks.getByName("publishShadowPublicationToMavenLocal") {
    dependsOn("publishPluginJavaDocsJar", "publishPluginJar")
}

tasks.getByName("generateMetadataFileForShadowPublication") {
    dependsOn("jar")
}
