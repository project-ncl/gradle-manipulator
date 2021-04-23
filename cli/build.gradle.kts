group = "org.jboss.gm"

dependencies {

    implementation("ch.qos.logback:logback-classic:${project.extra.get("logbackVersion")}")
    implementation("ch.qos.logback:logback-core:${project.extra.get("logbackVersion")}")

    // Minimum Gradle API to provide the Project. Not using gradleApi as that pulls in too much.
    implementation("org.gradle:gradle-core-api:${project.extra.get("gradleVersion")}")
    implementation("org.gradle:gradle-base-services:${project.extra.get("gradleVersion")}")

    api(project(":common"))
    implementation("org.gradle:gradle-tooling-api:${project.extra.get("gradleVersion")}")
    implementation("info.picocli:picocli:4.0.4")

    implementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    implementation("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")
    implementation("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    implementation("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    // Owner: Need Java8 dependency which pulls in owner itself.
    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    testRuntimeOnly("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    testImplementation(testFixtures(project(":common")))
    testImplementation(project(":analyzer"))
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")
    testImplementation("org.codehaus.plexus:plexus-archiver:4.2.3")
}

tasks {
    "jar"(Jar::class) {
        this.manifest {
            attributes["Main-Class"] = "org.jboss.gm.cli.Main"
        }
    }
}
