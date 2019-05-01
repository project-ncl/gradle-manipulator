group = "org.jboss.gm.common"

repositories {
    mavenCentral()
}

dependencies {
    compile("org.aeonbits.owner:owner-java8:1.0.10")
    compile("org.apache.commons:commons-lang3:3.8.1")
    compile("commons-io:commons-io:2.6")
    compile("com.fasterxml.jackson.core:jackson-databind:2.9.8")
    compile("org.commonjava.maven.ext:pom-manipulation-io:${extra.get("pmeVersion")}") {
        exclude ("ch.qos.logback", "logback-classic")
    }
    compile("org.commonjava.maven.ext:pom-manipulation-core:${extra.get("pmeVersion")}") {
        exclude ("ch.qos.logback", "logback-classic")
    }

    testCompile("junit", "junit", "4.12")
    testCompile("com.github.stefanbirkner:system-rules:1.19.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}