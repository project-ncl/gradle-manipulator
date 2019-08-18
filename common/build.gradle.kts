
group = "org.jboss.gm.common"

dependencies {
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    compile("org.aeonbits.owner:owner-java8:${project.extra.get("ownerVersion")}")
    compile("commons-lang:commons-lang:${project.extra.get("commonsVersion")}")
    compile("commons-io:commons-io:${project.extra.get("commonsVersion")}")
    compile("org.slf4j:slf4j-api:${project.extra.get("slf4jVersion")}")

    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")

    compile("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")

    testCompile("junit:junit:${project.extra.get("junitVersion")}")
    testCompile("com.github.stefanbirkner:system-rules:${project.extra.get("systemRulesVersion")}")
    testCompile("org.assertj:assertj-core:${project.extra.get("assertjVersion")}")
    testCompile(gradleApi())
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
