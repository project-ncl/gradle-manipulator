group = "org.jboss.gm.manipulation"

pluginBundle {
    website = "https://project-ncl.github.io/gradle-manipulator/"
    vcsUrl = "https://github.com/project-ncl/gradle-manipulator/tree/master/manipulation/tree/master/analyzer"
    tags = listOf("versions", "manipulation")
}

gradlePlugin {
    plugins {
        create("manipulationPlugin") {
            description = "Plugin that reads the alignment data from \${project.rootDir}/manipulation.json and configures build and publishing to use those versions"
            id = "org.jboss.gm.manipulation"
            implementationClass = "org.jboss.gm.manipulation.ManipulationPlugin"
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

    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    testCompile("junit:junit:${project.extra.get("junitVersion")}")
    testCompile("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testCompile("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")

    // GradleAPI in test compile to get access to org.gradle.internal.Pair
    testCompile(gradleApi())

    permitTestUnusedDeclared("junit:junit:${project.extra.get("junitVersion")}")
    permitTestUnusedDeclared("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    permitTestUnusedDeclared("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")

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
}

tasks.check { dependsOn(functionalTest) }
