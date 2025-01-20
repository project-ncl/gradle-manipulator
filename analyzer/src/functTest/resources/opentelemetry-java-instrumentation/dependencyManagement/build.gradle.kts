import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
  `java-platform`

  id("com.github.ben-manes.versions")
}

data class DependencySet(val group: String, val version: String, val modules: List<String>)

val dependencyVersions = hashMapOf<String, String>()
rootProject.extra["versions"] = dependencyVersions

// this line is managed by .github/scripts/update-sdk-version.sh
val otelVersion = "1.17.0"

rootProject.extra["otelVersion"] = otelVersion

// Need both BOM and groovy jars
val groovyVersion = "4.0.2"

// We don't force libraries we instrument to new versions since we compile and test against specific
// old baseline versions but we do try to force those libraries' transitive dependencies to new
// versions where possible so that we don't end up with explosion of dependency versions in
// Intellij, which causes Intellij to spend lots of time indexing all of those different dependency
// versions, and makes debugging painful because Intellij has no idea which dependency version's
// source to use when stepping through code.
//
// Sometimes libraries we instrument do require a specific version of a transitive dependency and
// that can be applied in the specific instrumentation gradle file, e.g.
// configurations.testRuntimeClasspath.resolutionStrategy.force "com.google.guava:guava:19.0"

val DEPENDENCY_BOMS = listOf(
  "com.fasterxml.jackson:jackson-bom:2.13.2.20220328",
  "com.google.guava:guava-bom:31.1-jre",
  "org.apache.groovy:groovy-bom:${groovyVersion}",
  "io.opentelemetry:opentelemetry-bom:${otelVersion}",
  "io.opentelemetry:opentelemetry-bom-alpha:${otelVersion}-alpha",
  "org.junit:junit-bom:5.8.2",
  "org.testcontainers:testcontainers-bom:1.17.1",
)

val DEPENDENCY_SETS = listOf(
  DependencySet(
    "com.google.auto.service",
    "1.0.1",
    listOf("auto-service", "auto-service-annotations")
  ),
  DependencySet(
    "com.google.auto.value",
    "1.9",
    listOf("auto-value", "auto-value-annotations")
  ),
  DependencySet(
    "com.google.errorprone",
    "2.14.0",
    listOf("error_prone_annotations", "error_prone_core", "error_prone_test_helpers")
  ),
  DependencySet(
    "net.bytebuddy",
    // When updating, also update conventions/build.gradle.kts
    "1.12.10",
    listOf("byte-buddy", "byte-buddy-dep", "byte-buddy-agent", "byte-buddy-gradle-plugin")
  ),
  DependencySet(
    "org.openjdk.jmh",
    "1.35",
    listOf("jmh-core", "jmh-generator-bytecode")
  ),
  DependencySet(
    "org.mockito",
    "4.5.1",
    listOf("mockito-core", "mockito-junit-jupiter", "mockito-inline")
  ),
  DependencySet(
    "org.slf4j",
    "1.7.36",
    listOf("slf4j-api", "slf4j-simple", "log4j-over-slf4j", "jcl-over-slf4j", "jul-to-slf4j")
  ),
)

// See the comment above about why we keep this rather large list.
// There are dependencies included here that appear to have no usages, but are maintained at
// this top level to help consistently satisfy large numbers of transitive dependencies.
val DEPENDENCIES = listOf(
  "ch.qos.logback:logback-classic:1.2.11",
  "com.github.stefanbirkner:system-lambda:1.2.1",
  "com.github.stefanbirkner:system-rules:1.19.0",
  "uk.org.webcompere:system-stubs-jupiter:2.0.1",
  "com.uber.nullaway:nullaway:0.9.7",
  "commons-beanutils:commons-beanutils:1.9.4",
  "commons-cli:commons-cli:1.5.0",
  "commons-codec:commons-codec:1.15",
  "commons-collections:commons-collections:3.2.2",
  "commons-digester:commons-digester:2.1",
  "commons-fileupload:commons-fileupload:1.4",
  "commons-io:commons-io:2.11.0",
  "commons-lang:commons-lang:2.6",
  "commons-logging:commons-logging:1.2",
  "commons-validator:commons-validator:1.7",
  "io.netty:netty:3.10.6.Final",
  "io.opentelemetry.proto:opentelemetry-proto:0.17.0-alpha",
  "org.assertj:assertj-core:3.22.0",
  "org.awaitility:awaitility:4.2.0",
  "com.google.code.findbugs:annotations:3.0.1u2",
  "com.google.code.findbugs:jsr305:3.0.2",
  "org.apache.groovy:groovy:${groovyVersion}",
  "org.apache.groovy:groovy-json:${groovyVersion}",
  "org.junit-pioneer:junit-pioneer:1.7.0",
  "org.objenesis:objenesis:3.2",
  "org.spockframework:spock-core:2.2-M1-groovy-4.0",
  "org.spockframework:spock-junit4:2.2-M1-groovy-4.0",
  "org.scala-lang:scala-library:2.11.12",
  // Note that this is only referenced as "org.springframework.boot" in build files, not the artifact name.
  "org.springframework.boot:spring-boot-dependencies:2.7.2"
)

javaPlatform {
  allowDependencies()
}

dependencies {
  for (bom in DEPENDENCY_BOMS) {
    api(enforcedPlatform(bom))
    val split = bom.split(':')
    dependencyVersions[split[0]] = split[2]
  }
  constraints {
    for (set in DEPENDENCY_SETS) {
      for (module in set.modules) {
        api("${set.group}:${module}:${set.version}")
        dependencyVersions[set.group] = set.version
      }
    }
    for (dependency in DEPENDENCIES) {
      api(dependency)
      val split = dependency.split(':')
      dependencyVersions[split[0]] = split[2]
    }
  }
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isGuava = version.endsWith("-jre")
  val isStable = stableKeyword || regex.matches(version) || isGuava
  return isStable.not()
}

tasks {
  named<DependencyUpdatesTask>("dependencyUpdates") {
    revision = "release"
    checkConstraints = true

    rejectVersionIf {
      isNonStable(candidate.version)
    }
  }
}
