plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

// Versions are kept in sync with gradle/dependencies.gradle via the mavenVersion property.
// buildSrc cannot apply the root dependencies.gradle, so we read it directly via a text match
// to keep a single source of truth. The coordinate literals here are what Dependabot tracks.
val mavenVersion: String = File(rootDir.parentFile, "gradle/dependencies.gradle")
    .readText()
    .let { Regex("""mavenVersion\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
    ?: error("mavenVersion not found in gradle/dependencies.gradle")

dependencies {
    implementation("org.apache.maven:maven-settings:$mavenVersion")
    implementation("org.apache.maven:maven-settings-builder:$mavenVersion")
}
