/*
 * Copyright (c) 2020, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

import aQute.bnd.gradle.Bundle
import aQute.bnd.gradle.BundleTaskConvention
import com.github.vlsi.gradle.dsl.configureEach
import com.github.vlsi.gradle.gettext.GettextTask
import com.github.vlsi.gradle.gettext.MsgAttribTask
import com.github.vlsi.gradle.gettext.MsgFmtTask
import com.github.vlsi.gradle.gettext.MsgMergeTask
import com.github.vlsi.gradle.license.GatherLicenseTask
import com.github.vlsi.gradle.properties.dsl.props

plugins {
    java
    id("biz.aQute.bnd.builder") version "4.3.1" apply false
    id("com.github.johnrengelman.shadow")
    id("com.github.lburgazzoli.karaf") version "0.5.1"
    id("com.github.vlsi.gettext") version "1.70"
    id("com.github.vlsi.gradle-extensions")
    id("com.github.vlsi.ide")
}

buildscript {
    repositories {
        // E.g. for biz.aQute.bnd.builder which is not published to Gradle Plugin Portal
        mavenCentral()
    }
}
setProperty("archivesBaseName", "postgresql-jre7")

val shaded by configurations.creating

val karafFeatures by configurations.creating {
    isTransitive = false
}

val String.v: String get() = rootProject.extra["$this.version"] as String
val skipReplicationTests by props()
val enableGettext by props()

if (skipReplicationTests) {
    tasks.configureEach<Test> {
        exclude("org/postgresql/replication/**")
        exclude("org/postgresql/test/jdbc2/CopyBothResponseTest*")
    }
}

tasks.configureEach<Test> {
    outputs.cacheIf("test results on the database configuration, so we can't cache it") {
        false
    }
}

tasks.configureEach<Jar> {
    manifest {
        attributes["Main-Class"] = "org.postgresql.util.PGJDBCMain"
        attributes["Automatic-Module-Name"] = "org.postgresql.jdbc"
    }
}

tasks.shadowJar {
    configurations = listOf(shaded)
    exclude("META-INF/maven/**")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
}

val osgiJar by tasks.registering(Bundle::class) {
    archiveClassifier.set("osgi")
    from(tasks.shadowJar.map { zipTree(it.archiveFile) })
    withConvention(BundleTaskConvention::class) {
        bnd(
            """
            -exportcontents: !org.postgresql.shaded.*, org.postgresql.*
            -removeheaders: Created-By
            Bundle-Descriptiona: Java JDBC driver for PostgreSQL database
            Bundle-DocURL: https://jdbc.postgresql.org/
            Bundle-Vendor: PostgreSQL Global Development Group
            Import-Package: javax.sql, javax.transaction.xa, javax.naming, javax.security.sasl;resolution:=optional, *;resolution:=optional
            Bundle-Activator: org.postgresql.osgi.PGBundleActivator
            Bundle-SymbolicName: org.postgresql.jdbc
            Bundle-Name: PostgreSQL JDBC Driver
            Bundle-Copyright: Copyright (c) 2003-2020, PostgreSQL Global Development Group
            Require-Capability: osgi.ee;filter:="(&(|(osgi.ee=J2SE)(osgi.ee=JavaSE))(version>=1.8))"
            Provide-Capability: osgi.service;effective:=active;objectClass=org.osgi.service.jdbc.DataSourceFactory
            """
        )
    }
}

karaf {
    features.apply {
        xsdVersion = "1.5.0"
        feature(closureOf<com.github.lburgazzoli.gradle.plugin.karaf.features.model.FeatureDescriptor> {
            name = "postgresql"
            description = "PostgreSQL JDBC driver karaf feature"
            version = project.version.toString()
            details = "Java JDBC 4.2 (JRE 8+) driver for PostgreSQL database"
            feature("transaction-api")
            includeProject = true
            bundle(project.group.toString(), closureOf<com.github.lburgazzoli.gradle.plugin.karaf.features.model.BundleDescriptor> {
                wrap = false
            })
            // List argument clears the "default" configurations
            configurations(listOf(karafFeatures))
        })
    }
}

// <editor-fold defaultstate="collapsed" desc="Trim checkerframework annotations from the source code">
val withoutAnnotations = layout.buildDirectory.dir("without-annotations").get().asFile

val sourceWithoutCheckerAnnotations by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = true
}

val hiddenAnnotation = Regex(
    "@(?:Nullable|NonNull|PolyNull|MonotonicNonNull|RequiresNonNull|EnsuresNonNull|" +
            "Regex|" +
            "Pure|" +
            "KeyFor|" +
            "Positive|NonNegative|IntRange|" +
            "GuardedBy|UnderInitialization|" +
            "DefaultQualifier)(?:\\([^)]*\\))?")
val hiddenImports = Regex("import org.checkerframework")

val removeTypeAnnotations by tasks.registering(Sync::class) {
    destinationDir = withoutAnnotations
    inputs.property("regexpsUpdatedOn", "2020-08-25")
    from(projectDir) {
        filteringCharset = `java.nio.charset`.StandardCharsets.UTF_8.name()
        filter { x: String ->
            x.replace(hiddenAnnotation, "/* $0 */")
                .replace(hiddenImports, "// $0")
        }
        include("src/**")
    }
}

(artifacts) {
    sourceWithoutCheckerAnnotations(withoutAnnotations) {
        builtBy(removeTypeAnnotations)
    }
}
// </editor-fold>


val extraMavenPublications by configurations.getting

(artifacts) {
    extraMavenPublications(osgiJar) {
        classifier = ""
    }
    extraMavenPublications(karaf.features.outputFile) {
        builtBy(tasks.named("generateFeatures"))
        classifier = "features"
    }
}
