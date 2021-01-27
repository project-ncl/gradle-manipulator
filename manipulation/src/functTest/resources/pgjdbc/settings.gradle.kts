/*
 * Copyright (c) 2019, PostgreSQL Global Development Group
 * See the LICENSE file in the project root for more information.
 */

// This is the name of a current project
// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder
rootProject.name = "pgjdbc"

include(
    "postgresql"
)

project(":postgresql").projectDir = file("pgjdbc")
