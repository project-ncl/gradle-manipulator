group = "org.jboss.pnc.gradle-manipulator"

val assertjVersion = project.property("assertjVersion") as String
val commonsIOVersion = project.property("commonsIOVersion") as String
val gradleVersion = project.property("gradleVersion") as String
val groovyVersion = project.property("groovyVersion") as String
val jgitVersion = project.property("jgitVersion") as String
val junitVersion = project.property("junitVersion") as String
val logbackVersion = project.property("logbackVersion") as String
val mavenVersion = project.property("mavenVersion") as String
val ownerVersion = project.property("ownerVersion") as String
val pmeVersion = project.property("pmeVersion") as String
val slf4jVersion = project.property("slf4jVersion") as String
val systemStubsVersion = project.property("systemStubsVersion") as String

dependencies {
    implementation("ch.qos.logback:logback-classic") { version { strictly(logbackVersion) } }
    implementation("ch.qos.logback:logback-core") { version { strictly(logbackVersion) } }

    // Minimum Gradle API to provide the Project. Not using gradleApi as that pulls in too much.
    implementation("org.gradle:gradle-core-api:$gradleVersion")
    implementation("org.gradle:gradle-base-services:$gradleVersion")

    // XXX: Versions 4.x > 4.0.1 suffer from <https://github.com/johnrengelman/shadow/issues/425>
    // To avoid this the CLI module for Gradle 4 avoids using api using implementation instead.
    if (GradleVersion.current() < GradleVersion.version("5.0.0")) {
        implementation(project(":common"))
    } else {
        api(project(":common"))
    }
    implementation("org.gradle:gradle-tooling-api:$gradleVersion")
    implementation("info.picocli:picocli:4.7.7")

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-core:$pmeVersion") {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common:$pmeVersion") {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-io:$pmeVersion") {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
    }

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.codehaus.groovy:groovy:$groovyVersion")

    // Owner: Need Java8 dependency which pulls in owner itself.
    implementation("org.aeonbits.owner:owner-java8:$ownerVersion")

    runtimeOnly("org.apache.maven:maven-core:$mavenVersion")
    runtimeOnly("org.apache.maven:maven-model:$mavenVersion")
    runtimeOnly("org.apache.maven:maven-artifact:$mavenVersion")

    testRuntimeOnly("commons-io:commons-io:$commonsIOVersion")
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))

    testImplementation(project(":analyzer"))
    testImplementation("junit:junit:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("uk.org.webcompere:system-stubs-junit4:$systemStubsVersion")
    testImplementation("org.codehaus.plexus:plexus-archiver:4.14.0")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
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
