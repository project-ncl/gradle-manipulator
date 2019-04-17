group = "org.jboss.gm.analyzer"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("alignmentPlugin") {
            id = "gm-analyzer"
            implementationClass = "org.jboss.gm.analyzer.alignment.AlignmentPlugin"
        }
    }
}

dependencies {
    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    compile("org.apache.commons:commons-lang3:3.8.1")
    compile("commons-io:commons-io:2.6")
    testCompile("junit:junit:4.12")
    testCompile("org.assertj:assertj-core:3.12.2")
    testCompile("org.mockito:mockito-core:2.27.0")
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
}

tasks.check { dependsOn(functionalTest) }