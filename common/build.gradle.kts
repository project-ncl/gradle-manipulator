
group = "org.jboss.gm.common"

dependencies {
    compileOnly(localGroovy())
    compileOnly(gradleApi())

    compile("org.aeonbits.owner:owner-java8:1.0.10")
    compile("commons-lang:commons-lang:2.6")
    compile("commons-io:commons-io:2.6")
    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    compile("org.commonjava.maven.ext:pom-manipulation-io:${project.extra.get("pmeVersion")}")
    compile("org.commonjava.maven.ext:pom-manipulation-core:${project.extra.get("pmeVersion")}")

    testCompile("junit", "junit", "4.12")
    testCompile("org.assertj:assertj-core:3.12.2")
    testCompile("com.github.stefanbirkner:system-rules:1.19.0")
    testCompile(gradleApi())
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
