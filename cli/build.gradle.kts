
group = "org.jboss.gm.cli"

dependencies {

    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("ch.qos.logback:logback-core:1.2.3")

    // Minimum Gradle API to provide the Project. Not using gradleApi as that pulls in too much.
    compile("org.gradle:gradle-core-api:5.6.2")

    compile(project(":common"))
    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")

    compile("org.gradle:gradle-tooling-api:5.6.2")

    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    compile("info.picocli:picocli:4.0.4")
    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")

    compile("org.codehaus.groovy:groovy:2.5.7")

    testCompile("junit:junit:${project.extra.get("junitVersion")}")

    // Lombok comes via plugin
    permitUsedUndeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
    permitTestUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")

    // Tooling API needs SLF4J
    permitUnusedDeclared("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    // Owner: Need Java8 dependency which pulls in owner itself.
    permitUnusedDeclared("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    permitUsedUndeclared("org.aeonbits.owner:owner:${project.extra.get("ownerVersion")}")

    permitUnusedDeclared("ch.qos.logback:logback-core:1.2.3")

    // The CLI needs to be able to run groovy.
    permitUnusedDeclared("org.codehaus.groovy:groovy:2.5.7")

    // The CLI needs to be able to run groovy.
    permitUnusedDeclared("org.gradle:gradle-core-api:5.6.2")
}

tasks {
    "jar"(Jar::class) {
        this.manifest {
            attributes["Main-Class"] = "org.jboss.gm.cli.Main"
        }
    }
}