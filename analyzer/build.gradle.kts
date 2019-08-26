group = "org.jboss.gm.analyzer"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

pluginBundle {
    website = "https://project-ncl.github.io/gradle-manipulator/"
    vcsUrl = "https://github.com/project-ncl/gradle-manipulator/tree/master/analyzer"
    tags = listOf("versions", "alignment")
}

gradlePlugin {
    plugins {
        create("alignmentPlugin") {
            description = "Plugin that that generates alignment metadata at \${project.rootDir}/manipulation.json"
            id = "org.jboss.gm.analyzer"
            implementationClass = "org.jboss.gm.analyzer.alignment.AlignmentPlugin"
            displayName = "GME Manipulation Plugin"
        }
    }
    // Disable creation of the plugin marker pom.
    this.isAutomatedPublishing = false
}

dependencies {
    compile(project(":common"))
    // the shadow configuration is used in order to avoid adding gradle and groovy stuff to the shadowed jar
    shadow(localGroovy())
    shadow(gradleApi())

    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    compile("org.apache.maven:maven-settings-builder:${project.extra.get("mavenVersion")}")
    compile("org.apache.maven:maven-settings:${project.extra.get("mavenVersion")}")

    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    compile("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    compile("commons-codec:commons-codec:1.11")

    compile("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")

    testCompile("junit:junit:${project.extra.get("junitVersion")}")
    testCompile("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")
    testCompile("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testCompile("org.jboss.byteman:byteman-bmunit:${project.extra.get("bytemanVersion")}")
    testCompile (files ("${System.getProperty("java.home")}/../lib/tools.jar") )
    testCompile("org.mockito:mockito-core:2.27.0")
    testCompile("com.github.tomakehurst:wiremock-jre8:2.23.2")

    // Groovy is built into Gradle
    permitUsedUndeclared("org.codehaus.groovy:groovy:2.5.7")

    // Owner: Need Java8 dependency which pulls in owner itself.
    permitUnusedDeclared("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    permitUsedUndeclared("org.aeonbits.owner:owner:${project.extra.get("ownerVersion")}")
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
    //this will be used in the Wiremock tests - the port needs to match what Wiremock is setup to use
    environment("DA_ENDPOINT_URL", "http://localhost:8089/da/rest/v-1")
}

tasks.check { dependsOn(functionalTest) }

tasks {
    //this is done in order to use the proper version in the init gradle files
    "processResources"(ProcessResources::class) {
        filesMatching("gme.gradle") {
            expand(project.properties)
        }
        filesMatching("analyzer-init.gradle") {
            expand(project.properties)
        }
    }
}
