plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.maven:maven-settings:3.9.12")
    implementation("org.apache.maven:maven-settings-builder:3.9.12")
}