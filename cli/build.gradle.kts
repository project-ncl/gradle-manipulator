
group = "org.jboss.gm.cli"

dependencies {

    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("ch.qos.logback:logback-core:1.2.3")

    compile(project(":common"))
    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")

    compile("org.gradle:gradle-tooling-api:5.6.2")
    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    compile("info.picocli:picocli:4.0.4")
    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")

    testCompile("junit:junit:${project.extra.get("junitVersion")}")

    // Lombok comes via plugin
    permitUsedUndeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
    permitTestUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")

    // Tooling API needs SLF4J
    permitUnusedDeclared("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    permitUnusedDeclared("ch.qos.logback:logback-core:1.2.3")
}

tasks {
    "jar"(Jar::class) {
        this.manifest {
            attributes["Main-Class"] = "org.jboss.gm.cli.Main"
        }
    }
}