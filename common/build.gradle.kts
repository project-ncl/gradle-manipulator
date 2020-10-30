
group = "org.jboss.gm"

plugins {
    `java-test-fixtures`
}

dependencies {
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    compile("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    compile("commons-io:commons-io:${project.extra.get("commonsVersion")}")

    compile("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    compile("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    compile("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    compile("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}")

    runtime("org.apache.maven:maven-core:${project.extra.get("mavenVersion")}")
    runtime("org.apache.maven:maven-model:${project.extra.get("mavenVersion")}")
    runtime("org.apache.maven:maven-artifact:${project.extra.get("mavenVersion")}")

    // This is to prevent compilation errors in conjunction with Lombok due to use of PME code.
    compileOnly("org.apache.maven:maven-compat:3.5.0")
    permitUnusedDeclared("org.apache.maven:maven-compat:3.5.0")

    testFixturesImplementation(gradleApi())
    testFixturesImplementation("junit:junit:${project.extra.get("junitVersion")}")
    testCompile("junit:junit:${project.extra.get("junitVersion")}")
    testCompile("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")
    testCompile("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testCompile(gradleApi())

    // Lombok comes via plugin
    permitUsedUndeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
    permitTestUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")

    // Groovy is built into Gradle
    permitUsedUndeclared("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    // Owner: Need Java8 dependency which pulls in owner itself.
    permitUnusedDeclared("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    permitUsedUndeclared("org.aeonbits.owner:owner:${project.extra.get("ownerVersion")}")
}
