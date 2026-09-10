group = "org.jboss.pnc.gradle-manipulator"

val logbackClassic = project.property("logbackClassic").toString()
val logbackCore = project.property("logbackCore").toString()
val gradleCoreApi = project.property("gradleCoreApi").toString()
val gradleBaseServices = project.property("gradleBaseServices").toString()
val gradleToolingApi = project.property("gradleToolingApi").toString()
val picocli = project.property("picocli").toString()
val pmeCore = project.property("pmeCore").toString()
val pmeCommon = project.property("pmeCommon").toString()
val pmeIO = project.property("pmeIO").toString()
val slf4jApi = project.property("slf4jApi").toString()
val groovy = project.property("groovy").toString()
val ownerJava8 = project.property("ownerJava8").toString()
val mavenCore = project.property("mavenCore").toString()
val mavenModel = project.property("mavenModel").toString()
val mavenArtifact = project.property("mavenArtifact").toString()
val commonsIO = project.property("commonsIO").toString()
val junit = project.property("junit").toString()
val assertjCore = project.property("assertjCore").toString()
val systemStubsJunit4 = project.property("systemStubsJunit4").toString()
val plexusArchiver = project.property("plexusArchiver").toString()
val jgit = project.property("jgit").toString()

dependencies {
    // logback uses strictly() to pin the version across all transitive paths
    val logbackVersion = project.property("logbackVersion").toString()
    implementation("ch.qos.logback:logback-classic") { version { strictly(logbackVersion) } }
    implementation("ch.qos.logback:logback-core") { version { strictly(logbackVersion) } }

    // Minimum Gradle API to provide the Project. Not using gradleApi as that pulls in too much.
    implementation(gradleCoreApi)
    implementation(gradleBaseServices)

    // XXX: Versions 4.x > 4.0.1 suffer from <https://github.com/johnrengelman/shadow/issues/425>
    // To avoid this the CLI module for Gradle 4 avoids using api using implementation instead.
    if (GradleVersion.current() < GradleVersion.version("5.0.0")) {
        implementation(project(":common"))
    } else {
        api(project(":common"))
    }
    implementation(gradleToolingApi)
    implementation(picocli)

    implementation(pmeCore) {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
    }

    implementation(pmeCommon) {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
    }

    implementation(pmeIO) {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
    }

    implementation(slf4jApi)
    implementation(groovy)

    // Owner: Need Java8 dependency which pulls in owner itself.
    implementation(ownerJava8)

    runtimeOnly(mavenCore)
    runtimeOnly(mavenModel)
    runtimeOnly(mavenArtifact)

    testRuntimeOnly(commonsIO)
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))

    testImplementation(project(":analyzer"))
    testImplementation(junit)
    testImplementation(assertjCore)
    testImplementation(systemStubsJunit4)
    testImplementation(plexusArchiver)
    testImplementation(jgit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks { "jar"(Jar::class) { manifest { attributes["Main-Class"] = "org.jboss.pnc.gradlemanipulator.cli.Main" } } }

// Implicit dependencies detected by Gradle 7
// See <https://docs.gradle.org/7.0/userguide/validation_problems.html#implicit_dependency>
tasks.named("test") { dependsOn("shadowJar") }

if (GradleVersion.current() >= GradleVersion.version("5.0")) {
    tasks.named("generateMetadataFileForShadowPublication") { dependsOn("jar") }
}
