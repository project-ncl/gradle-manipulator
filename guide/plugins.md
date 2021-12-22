---
title: Plugins
---

* Contents
{:toc}

### Plugin Removal

GME supports removal of plugins prior to running the alignment phase. Note that plugin removal is
only supported through use of the CLI. If the property `-DpluginRemoval=<plugin-id>,....` is set, it
will activate removal for the specified plugin. It will remove references to the plugin activation
and the respective configuration block. The plugin-id value should be from the table below and may be
a comma separated list.

The current supported list of plugins are:

| Plugin ID    | Configuration Block |
|------------|---------------------|
| `com.github.ben-manes.versions` | `dependencyUpdates`  |
| `com.github.burrunan.s3-build-cache` | `buildCache`  |
| `gradle-enterprise` | `gradleEnterprise`  |
| `net.vivin.gradle-semantic-build-versioning` | `preRelease` |

Note that this only supports direct removal of configuration blocks, not using any form of the [task
avoidance API](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html) For example:

Note that `net.vivin.gradle-semantic-build-versioning` should **not** be explicitly stated as it is handled
implicitly when that plugin is processed - see below.

```
if (isCiServer) {
    gradleEnterprise {
        buildScan {
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
            tag("CI")
        }
    }
}
```

The above will be removed leaving only the empty 'if' block.

### Gradle Semanatic Build Versioning Plugin

This plugin (see [here](https://github.com/vivin/gradle-semantic-build-versioning)) provides support for
automatic semantic versioning of builds based off git commit information.

GME supports handling this plugin through the CLI tool *only*. If this plugin is detected then GME will first
establish the actual version via the [printVersion](https://github.com/vivin/gradle-semantic-build-versioning#printversion)
command, inserting that into the `gradle.properties`. Finally it will remove the plugin to ensure the version
is kept static.
