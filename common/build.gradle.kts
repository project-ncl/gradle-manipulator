group = "org.jboss.pnc.gradle-manipulator"

dependencies {
    runtimeOnly("org.apache.ivy:ivy:${project.extra.get("ivyVersion")}")
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    implementation("org.apache.commons:commons-lang3:${project.extra.get("commonsLangVersion")}")
    implementation("commons-io:commons-io:${project.extra.get("commonsIOVersion")}")
    implementation("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    implementation("org.commonjava.atlas:atlas-identities:${project.extra.get("atlasVersion")}") {
        exclude(group = "ch.qos.logback")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonAnnotationsVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    implementation("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    implementation("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-common-lite:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-core:${project.extra.get("pmeVersion")}") {
        // Only needed for the Groovy references
        isTransitive = false
    }

    implementation("org.jboss.pnc.maven-manipulator:pom-manipulation-io:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    // This is a gigantic hack to avoid "Protocol message contained an invalid tag (zero).". The otel dependency
    // contains kotlin-stdlib:2.x which conflicts horribly with the TestKit.
    // Note for future - tried various combinations including an explicit kotlin override and
    //  an exclusion for kotlin but none worked.
    if (GradleVersion.current() == GradleVersion.version("6.5.1")) {
        logger.warn("Using older opentelemetry-ext-cli-java for 6.5.1")
        implementation("org.jboss.pnc.otel:opentelemetry-ext-cli-java:2.0.0")
    } else {
        implementation("org.jboss.pnc.otel:opentelemetry-ext-cli-java:${project.extra.get("opentelemetryVersion")}")
    }

    // This is to prevent compilation errors in conjunction with Lombok due to use of PME code.
    compileOnly("org.apache.maven:maven-compat:${project.extra.get("mavenVersion")}")

    testFixturesCompile("org.codehaus.plexus:plexus-archiver:4.10.4")
    testFixturesCompile("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
}
