group = "org.jboss.gm.manipulation"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("manipulationPlugin") {
            id = "org.jboss.gm.manipulation"
            implementationClass = "org.jboss.gm.manipulation.ManipulationPlugin"
        }
    }
}

dependencies {
    compile(project(":common"))
    compile("org.apache.commons:commons-lang3:3.8.1")
    testCompile("junit:junit:4.12")
    testCompile("org.assertj:assertj-core:3.12.2")
}

// separate source set and task for functional tests

sourceSets.create("functionalTest") {
    java.srcDir("src/functTest/java")
    resources.srcDir("src/functTest/resources")
    compileClasspath += sourceSets["main"].output + configurations.testRuntime
    runtimeClasspath += output + compileClasspath
}

val functionalTest = task<Test>("functionalTest") {
    description = "Runs functional tests"
    group = "verification"
    testClassesDirs = sourceSets["functionalTest"].output.classesDirs
    classpath = sourceSets["functionalTest"].runtimeClasspath
    mustRunAfter(tasks["test"])
    environment("BYPASS_ALIGNER", "true")
}

tasks.check { dependsOn(functionalTest) }