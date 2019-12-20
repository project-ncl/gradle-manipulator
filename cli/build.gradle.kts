group = "org.jboss.gm"

dependencies {

    compile("ch.qos.logback:logback-classic:${project.extra.get("logbackVersion")}")
    compile("ch.qos.logback:logback-core:${project.extra.get("logbackVersion")}")

    // Minimum Gradle API to provide the Project. Not using gradleApi as that pulls in too much.
    compile("org.gradle:gradle-core-api:${project.extra.get("gradleVersion")}")

    compile(project(":common"))
    compile("org.gradle:gradle-tooling-api:${project.extra.get("gradleVersion")}")
    compile("info.picocli:picocli:4.0.4")

    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")
    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    compile("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    testCompile("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    testCompile(project(":analyzer"))
    testCompile("junit:junit:${project.extra.get("junitVersion")}")
    testCompile("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")

    // Lombok comes via plugin
    permitUsedUndeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
    permitTestUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")

    // Tooling API needs SLF4J
    permitUnusedDeclared("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    // Owner: Need Java8 dependency which pulls in owner itself.
    permitUnusedDeclared("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    permitUsedUndeclared("org.aeonbits.owner:owner:${project.extra.get("ownerVersion")}")

    permitUnusedDeclared("ch.qos.logback:logback-core:${project.extra.get("logbackVersion")}")

    // The CLI needs to be able to run groovy.
    permitUnusedDeclared("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    // The CLI needs to be able to run groovy.
    permitUnusedDeclared("org.gradle:gradle-core-api:${project.extra.get("gradleVersion")}")
}

tasks {
    "jar"(Jar::class) {
        this.manifest {
            attributes["Main-Class"] = "org.jboss.gm.cli.Main"
        }
    }
}
