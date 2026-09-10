group = "org.jboss.pnc.gradle-manipulator"

val ivy = project.property("ivy").toString()
val ownerJava8 = project.property("ownerJava8").toString()
val commonsLang3 = project.property("commonsLang3").toString()
val commonsIO = project.property("commonsIO").toString()
val commonsBeanutils = project.property("commonsBeanutils").toString()
val atlasIdentities = project.property("atlasIdentities").toString()
val slf4jApi = project.property("slf4jApi").toString()
val groovy = project.property("groovy").toString()
val pmeCommonLite = project.property("pmeCommonLite").toString()
val pmeCore = project.property("pmeCore").toString()
val pmeIO = project.property("pmeIO").toString()
val mavenCore = project.property("mavenCore").toString()
val mavenModel = project.property("mavenModel").toString()
val mavenArtifact = project.property("mavenArtifact").toString()
val opentelemetryExtCli = project.property("opentelemetryExtCli").toString()
val mavenCompat = project.property("mavenCompat").toString()
val plexusArchiver = project.property("plexusArchiver").toString()
val assertjCore = project.property("assertjCore").toString()
val junit = project.property("junit").toString()
val systemStubsJunit4 = project.property("systemStubsJunit4").toString()

dependencies {
    runtimeOnly(ivy)
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    implementation(ownerJava8)
    implementation(commonsLang3)
    implementation(commonsIO)
    implementation(commonsBeanutils)

    implementation(atlasIdentities) {
        exclude(group = "ch.qos.logback")
    }

    implementation(slf4jApi)
    implementation(groovy)

    implementation(pmeCommonLite) {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    implementation(pmeCore) {
        // Only needed for the Groovy references
        isTransitive = false
    }

    implementation(pmeIO) {
        exclude(group = "ch.qos.logback")
        exclude(group = "org.commonjava.maven.galley")
        // Exclude until new release due to Quarkus bom
        exclude(group = "org.jboss.pnc.otel")
    }

    runtimeOnly(mavenCore)
    runtimeOnly(mavenModel)
    runtimeOnly(mavenArtifact)

    // This is a gigantic hack to avoid "Protocol message contained an invalid tag (zero).". The otel dependency
    // contains kotlin-stdlib:2.x which conflicts horribly with the TestKit.
    // Note for future - tried various combinations including an explicit kotlin override and
    //  an exclusion for kotlin but none worked.
    if (GradleVersion.current() == GradleVersion.version("6.5.1")) {
        logger.warn("Using older opentelemetry-ext-cli-java for 6.5.1")
        implementation("org.jboss.pnc.otel:opentelemetry-ext-cli-java:2.0.0")
    } else {
        implementation(opentelemetryExtCli)
    }

    // This is to prevent compilation errors in conjunction with Lombok due to use of PME code.
    compileOnly(mavenCompat)

    testFixturesCompile(plexusArchiver)
    testFixturesCompile(assertjCore)
    testImplementation(junit)
    testImplementation(systemStubsJunit4)
}
