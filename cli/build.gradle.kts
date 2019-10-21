
group = "org.jboss.gm.cli"

dependencies {

    //    compile(project(":analyzer"))
//    // the shadow configuration is used in order to avoid adding gradle and groovy stuff to the shadowed jar
//    shadow(localGroovy())
//    shadow(gradleApi())

//    compile("org.gradle:gradle-logging:5.6.2")

    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("ch.qos.logback:logback-core:1.2.3")

    compile(project(":common"))
    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")

    compile("org.gradle:gradle-tooling-api:5.6.2")
    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    compile("info.picocli:picocli:4.0.4")
    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")


    // Lombok comes via plugin
    permitUsedUndeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")
    permitTestUnusedDeclared("org.projectlombok:lombok:${project.extra.get("lombokVersion")}")

    // Tooling API needs SLF4J
    permitUnusedDeclared("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    // Our GMLogger requires the Gradle logging API.
    // permitUnusedDeclared("org.gradle:gradle-logging:5.6.2")

    // TODO: QOS Test
//    permitUnusedDeclared("ch.qos.logback:logback-classic:1.2.3")
    permitUnusedDeclared("ch.qos.logback:logback-core:1.2.3")

    // TODO: Remove when we use them....
//    permitUsedUndeclared("org.jboss.gm.analyzer:analyzer:1.4-SNAPSHOT")
//    permitUsedUndeclared("org.jboss.gm.analyzer:common:1.4-SNAPSHOT")

}

// separate source set and task for functional tests

//sourceSets {
//    create("functionalTest") {
//        java.srcDir("src/functTest/java")
//        resources.srcDir("src/functTest/resources")
//        compileClasspath += sourceSets["main"].output + configurations.testRuntime
//        runtimeClasspath += output + compileClasspath
//    }
//}
//
//val functionalTest = task<Test>("functionalTest") {
//    description = "Runs functional tests"
//    group = "verification"
//    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
//    classpath = sourceSets["functionalTest"].runtimeClasspath
//    mustRunAfter(tasks["test"])
//    //this will be used in the Wiremock tests - the port needs to match what Wiremock is setup to use
//    environment("DA_ENDPOINT_URL", "http://localhost:8089/da/rest/v-1")
//}
//
//val testJar by tasks.registering(Jar::class) {
//    mustRunAfter(tasks["functionalTest"])
//    archiveClassifier.set("tests")
//    from(sourceSets["functionalTest"].output)
//    from(sourceSets.test.get().output)
//}


//configure<PublishingExtension> {
//    publications {
//        getByName<MavenPublication>("shadow") {
//            artifact(testJar.get())
//        }
//    }
//}
//
//tasks.check { dependsOn(functionalTest) }

tasks {
    "jar"(Jar::class) {
        this.manifest {
            attributes["Main-Class"] = "org.jboss.gm.cli.Main"
        }
    }
}