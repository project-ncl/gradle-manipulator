group = "org.jboss.gm"

dependencies {
    runtimeOnly("org.apache.ivy:ivy:${project.extra.get("ivyVersion")}")
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    implementation("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    implementation("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    implementation("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    implementation("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    implementation("com.fasterxml.jackson.core:jackson-databind:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    implementation("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    implementation("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    implementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-core")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        }

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


    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    implementation("com.redhat.resilience.otel:opentelemetry-ext-cli-java:${project.extra.get("opentelemetryVersion")}")

    // This is to prevent compilation errors in conjunction with Lombok due to use of PME code.
    compileOnly("org.apache.maven:maven-compat:${project.extra.get("mavenVersion")}")

    testFixturesCompile("org.codehaus.plexus:plexus-archiver:4.2.3")
    testFixturesCompile("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("uk.org.webcompere:system-stubs-junit4:${project.extra.get("systemStubsVersion")}")
    testImplementation(gradleApi())
}
