/*
 * Copyright (c) 2019, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

// This is the name of a current project
// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder
rootProject.name = "pgjdbc"

// See https://github.com/gradle/gradle/issues/1348#issuecomment-284758705 and
// https://github.com/gradle/gradle/issues/5321#issuecomment-387561204
// Gradle inherits Ant "default excludes", however we do want to archive those files
org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitattributes")
org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitignore")

fun property(name: String) =
    when (extra.has(name)) {
        true -> extra.get(name) as? String
        else -> null
    }
