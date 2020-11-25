group = "org.jboss.gm"

pluginBundle {
    website = "https://project-ncl.github.io/gradle-manipulator/"
    vcsUrl = "https://github.com/project-ncl/gradle-manipulator/tree/master/analyzer"
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
    // the shadow configuration is used in order to avoid adding gradle and groovy stuff to the shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    implementation("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")
    implementation("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}")
    implementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
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
    testImplementation(testFixtures(project(":common")))
    testImplementation(project(":common"))
    testImplementation(gradleTestKit())
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("org.jboss.byteman:byteman-bmunit:${project.extra.get("bytemanVersion")}")
    testImplementation(files ("${System.getProperty("java.home")}/../lib/tools.jar") )
    testImplementation("org.mockito:mockito-core:2.27.0")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.26.3")
    testImplementation(gradleKotlinDsl())
}

tasks.test {
    systemProperties["jdk.attach.allowAttachSelf"] = "true"
}

// separate source set and task for functional tests

val functionalTestSourceSet = sourceSets.create("functionalTest") {
    java.srcDir("src/functTest/java")
    resources.srcDir("src/functTest/resources")
    compileClasspath += sourceSets["main"].output + configurations.testRuntime
    runtimeClasspath += output + compileClasspath
}
configurations.getByName("functionalTestImplementation").apply {
    extendsFrom(configurations.getByName("testImplementation"))
}
configurations.getByName("functionalTestRuntime").apply {
    extendsFrom(configurations.getByName("testRuntime"))
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
    //this will be used in the Wiremock tests - the port needs to match what Wiremock is setup to use
    environment("DA_ENDPOINT_URL", "http://localhost:8089/da/rest/v-1")
    systemProperties["jdk.attach.allowAttachSelf"] = "true"
}

val testJar by tasks.registering(Jar::class) {
    mustRunAfter(tasks["functionalTest"])
    archiveClassifier.set("tests")
    from(sourceSets["functionalTest"].output)
    from(sourceSets.test.get().output)
}

// Publish test source jar so it can be reused by manipulator-groovy-examples.
val testSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("test-sources")
    from(sourceSets.test.get().allSource)
    from(sourceSets.getByName("functionalTest").java.srcDirs)
}

configure<PublishingExtension> {
    publications {
        getByName<MavenPublication>("shadow") {
            artifact(testJar.get())
            artifact(testSourcesJar.get())
        }
    }
}

tasks.check { dependsOn(functionalTest) }

tasks {
    //this is done in order to use the proper version in the init gradle files
    "processResources"(ProcessResources::class) {
        filesMatching("gme.gradle") {
            expand(project.properties)
        }
        filesMatching("analyzer-init.gradle") {
            expand(project.properties)
        }
    }
}
