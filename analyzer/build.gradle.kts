group = "org.jboss.gm"

pluginBundle {
    website = "https://project-ncl.github.io/gradle-manipulator/"
    vcsUrl = "https://github.com/project-ncl/gradle-manipulator/tree/main/analyzer"
    tags = listOf("versions", "alignment")
}

gradlePlugin {
    plugins {
        create("alignmentPlugin") {
            description = "Plugin that that generates alignment metadata at \${project.rootDir}/manipulation.json"
            id = "org.jboss.gm.analyzer"
            implementationClass = "org.jboss.gm.analyzer.alignment.AlignmentPlugin"
            displayName = "GME Manipulation Plugin"
        }
    }

    // Disable creation of the plugin marker pom.
    this.isAutomatedPublishing = false
}

dependencies {
    implementation(project(":common"))
    // The shadow configuration is used in order to avoid adding gradle and groovy stuff to the shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    implementation("com.fasterxml.jackson.core:jackson-databind:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    implementation("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        }

    implementation("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        }

    implementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        }


    implementation("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")

    implementation("org.apache.maven:maven-settings-builder:${project.extra.get("mavenVersion")}")
    implementation("org.apache.maven:maven-settings:${project.extra.get("mavenVersion")}")

    implementation("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    implementation("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    implementation("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    testRuntimeOnly("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    testImplementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("org.jboss.byteman:byteman-bmunit:${project.extra.get("bytemanVersion")}")
    testImplementation(files ("${System.getProperty("java.home")}/../lib/tools.jar") )
    testImplementation("org.mockito:mockito-core:2.27.0")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.26.3")
    testImplementation(gradleKotlinDsl())
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:${project.extra.get("jgitVersion")}")
    testImplementation("pl.pragmatists:JUnitParams:1.1.1")
}

tasks.withType<Test>().configureEach {
    systemProperties["jdk.attach.allowAttachSelf"] = "true"
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
    // This will be used in the Wiremock tests - the port needs to match what Wiremock is set up to use
    environment("DA_ENDPOINT_URL", "http://localhost:8089/da/rest/v-1")
    systemProperties["jdk.attach.allowAttachSelf"] = "true"
}

val testJar by tasks.registering(Jar::class) {
    mustRunAfter(tasks["functionalTest"])
    archiveClassifier.set("tests")
    from(sourceSets["functionalTest"].output)
    from(sourceSets["test"].output)
}

// Publish test source jar so it can be reused by manipulator-groovy-examples.
val testSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("test-sources")
    from(sourceSets["test"].allSource)
    from(sourceSets["functionalTest"].java.srcDirs)
}

configure<PublishingExtension> {
    publications {
        getByName<MavenPublication>("shadow") {
            artifact(testJar.get())
            artifact(testSourcesJar.get())
        }
    }
}

tasks {
    // This is done in order to use the proper version in the init gradle files
    "processResources"(ProcessResources::class) {
        filesMatching("gme.gradle") {
            expand(project.properties)
        }
        filesMatching("analyzer-init.gradle") {
            expand(project.properties)
        }
    }
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
