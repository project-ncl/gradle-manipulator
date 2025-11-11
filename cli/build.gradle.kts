group = "org.jboss.gm"

dependencies {
    implementation("ch.qos.logback:logback-classic") { version { strictly("${project.extra.get("logbackVersion")}") } }
    implementation("ch.qos.logback:logback-core") { version { strictly("${project.extra.get("logbackVersion")}") } }

    // Minimum Gradle API to provide the Project. Not using gradleApi as that pulls in too much.
    implementation("org.gradle:gradle-core-api:${project.extra.get("gradleVersion")}")
    implementation("org.gradle:gradle-base-services:${project.extra.get("gradleVersion")}")

    api(project(":common"))
    implementation("org.gradle:gradle-tooling-api:${project.extra.get("gradleVersion")}")
    implementation("info.picocli:picocli:4.6.3")

    implementation("com.fasterxml.jackson.core:jackson-databind:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-core:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-io:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    }

    implementation("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    implementation("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    // Owner: Need Java8 dependency which pulls in owner itself.
    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    testRuntimeOnly("commons-io:commons-io:${project.extra.get("commonsIOVersion")}")
    testImplementation(project(path = ":common", configuration = "testFixturesCompile"))

    testImplementation(project(":analyzer"))
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
    testImplementation("org.codehaus.plexus:plexus-archiver:4.2.3")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:${project.extra.get("jgitVersion")}")
}

tasks { "jar"(Jar::class) { manifest { attributes["Main-Class"] = "org.jboss.gm.cli.Main" } } }

// Implicit dependencies detected by Gradle 7
// See <https://docs.gradle.org/7.0/userguide/validation_problems.html#implicit_dependency>
tasks.named("test") { dependsOn("shadowJar") }

if (GradleVersion.current() >= GradleVersion.version("5.0")) {
    tasks.named("generateMetadataFileForShadowPublication") { dependsOn("jar") }
}
