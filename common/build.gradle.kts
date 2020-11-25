
group = "org.jboss.gm"

plugins {
    `java-test-fixtures`
}

dependencies {
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    implementation("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    implementation("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    implementation("commons-io:commons-io:${project.extra.get("commonsVersion")}")

    implementation("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    implementation("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    implementation("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    implementation("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    implementation("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")
    implementation("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}")

    runtimeOnly("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtimeOnly("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    // This is to prevent compilation errors in conjunction with Lombok due to use of PME code.
    compileOnly("org.apache.maven:maven-compat:3.5.0")
//    permitUnusedDeclared("org.apache.maven:maven-compat:3.5.0")

    testFixturesImplementation(gradleApi())
    testFixturesImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testImplementation("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")
    testImplementation("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testImplementation(gradleApi())

    // Lombok comes via plugin
//    permitUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
//    permitTestUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
//
//    // Groovy is built into Gradle
//    permitUsedUndeclared("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")
//
//    // Owner: Need Java8 dependency which pulls in owner itself.
//    permitUnusedDeclared("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
//    permitUsedUndeclared("org.aeonbits.owner:owner:${project.extra.get("ownerVersion")}")
//
//    // Bug in dependency analyse
//    permitUnusedDeclared("commons-io:commons-io:${project.extra.get("commonsVersion")}")
//    permitUsedUndeclared("commons-io:commons-io:2.5")

}
