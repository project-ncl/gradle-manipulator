
group = "org.jboss.gm.common"

dependencies {
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    compile("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    compile("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    compile("commons-beanutils:commons-beanutils:${project.extra.get("commonsBeanVersion")}")

    compile("org.commonjava.maven.atlas:atlas-identities:${project.extra.get("atlasVersion")}")

    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    compile("com.fasterxml.jackson.core:jackson-annotations:${project.extra.get("jacksonVersion")}")
    compile("com.fasterxml.jackson.core:jackson-core:${project.extra.get("jacksonVersion")}")

    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")
    compile("org.codehaus.groovy:groovy:${project.extra.get("groovyVersion")}")

    compile("org.commonjava.maven.ext:pom-manipulation-common:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")

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
