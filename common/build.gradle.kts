group = "org.jboss.pnc.gradle-manipulator"

val assertjVersion = project.property("assertjVersion") as String
val atlasVersion = project.property("atlasVersion") as String
val commonsBeanVersion = project.property("commonsBeanVersion") as String
val commonsIOVersion = project.property("commonsIOVersion") as String
val commonsLangVersion = project.property("commonsLangVersion") as String
val groovyVersion = project.property("groovyVersion") as String
val ivyVersion = project.property("ivyVersion") as String
val junitVersion = project.property("junitVersion") as String
val mavenVersion = project.property("mavenVersion") as String
val opentelemetryVersion = project.property("opentelemetryVersion") as String
val ownerVersion = project.property("ownerVersion") as String
val pmeVersion = project.property("pmeVersion") as String
val slf4jVersion = project.property("slf4jVersion") as String
val systemStubsVersion = project.property("systemStubsVersion") as String

dependencies {
    runtimeOnly("org.apache.ivy:ivy:$ivyVersion")
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    implementation("org.aeonbits.owner:owner-java8:$ownerVersion")
    implementation("org.apache.commons:commons-lang3:$commonsLangVersion")
    implementation("commons-io:commons-io:$commonsIOVersion")
    implementation("commons-beanutils:commons-beanutils:$commonsBeanVersion")

    implementation("org.commonjava.atlas:atlas-identities:$atlasVersion") {
        exclude(group = "ch.qos.logback")
    }

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.codehaus.groovy:groovy:$groovyVersion")

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common-lite:$pmeVersion") {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-core:$pmeVersion") {
        // Only needed for the Groovy references
        isTransitive = false
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-io:$pmeVersion") {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    runtimeOnly("org.apache.maven:maven-core:$mavenVersion")
    runtimeOnly("org.apache.maven:maven-model:$mavenVersion")
    runtimeOnly("org.apache.maven:maven-artifact:$mavenVersion")

    // This is a gigantic hack to avoid "Protocol message contained an invalid tag (zero).". The otel dependency
    // contains kotlin-stdlib:2.x which conflicts horribly with the TestKit.
    // Note for future - tried various combinations including an explicit kotlin override and
    //  an exclusion for kotlin but none worked.
    if (GradleVersion.current() == GradleVersion.version("6.5.1")) {
        logger.warn("Using older opentelemetry-ext-cli-java for 6.5.1")
        implementation("org.jboss.pnc.otel:opentelemetry-ext-cli-java:2.0.0")
    } else {
        implementation("org.jboss.pnc.otel:opentelemetry-ext-cli-java:$opentelemetryVersion")
    }

    // This is to prevent compilation errors in conjunction with Lombok due to use of PME code.
    compileOnly("org.apache.maven:maven-compat:$mavenVersion")

    testFixturesCompile("org.codehaus.plexus:plexus-archiver:4.14.0")
    testFixturesCompile("org.assertj:assertj-core:$assertjVersion")
    testImplementation("junit:junit:$junitVersion")
    testImplementation("uk.org.webcompere:system-stubs-junit4:$systemStubsVersion")
}
