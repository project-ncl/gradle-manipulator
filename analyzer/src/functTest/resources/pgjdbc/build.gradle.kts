/*
 * Copyright (c) 2019, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */


plugins {
    java
    id("org.jboss.gm.analyzer")

}

val String.v: String get() = rootProject.extra["$this.version"] as String
val buildVersion = "pgjdbc".v
println("Building pgjdbc $buildVersion")

allprojects {

    group = "org.postgresql"
    version = buildVersion

    val javaMainUsed = file("src/main/java").isDirectory
    val javaTestUsed = file("src/test/java").isDirectory
    val kotlinMainUsed = file("src/main/kotlin").isDirectory
    val kotlinTestUsed = file("src/test/kotlin").isDirectory
    val kotlinUsed = kotlinMainUsed || kotlinTestUsed
    if (kotlinUsed) {
        apply(plugin = "java-library")
        apply(plugin = "org.jetbrains.kotlin.jvm")
        dependencies {
            add(if (kotlinMainUsed) "implementation" else "testImplementation", kotlin("stdlib"))
        }
    }
}
