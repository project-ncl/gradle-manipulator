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
a comma separated list. It will examine every `*.gradle` and `*.gradle.kts` in the repository.

The current supported list of plugins are:

| Plugin ID    | Configuration Block | Tasks |
|------------|---------------------|
| `com.github.ben-manes.versions` | `dependencyUpdates`  | |
| `com.github.burrunan.s3-build-cache` | `buildCache`  | |
| `de.marcphilipp.nexus-publish` | `nexusPublishing`  | |
| `gradle-enterprise` | `gradleEnterprise`  | |
| `io.codearte.nexus-staging` | `nexusStaging`  | `closeRepository`, `releaseRepository`, `closeAndReleaseRepository` |
| `io.github.gradle-nexus.publish-plugin` | `nexusPublishing`  | |
| `signing` | `signing`  | |
| `net.vivin.gradle-semantic-build-versioning` | `preRelease` | |

<table bgcolor="#00ff99">
<tr>
<td>
    A special shortcut of <code>ALL</code> is available to represent removing all supported plugins.
</td>
</tr>
</table>


Note that this only supports direct removal of configuration blocks, not using any form of the [task
avoidance API](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html) For example:

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

Note that `net.vivin.gradle-semantic-build-versioning` should **not** be explicitly stated as it is handled
implicitly when that plugin is processed - see below.

Tasks are also removed if they are mentioned within the build scripts. Note that it only removes simplistic
**single** lines. Multiple line blocks with braces are **not** supported. e.g.
```
publish.finalizedBy closeAndReleaseRepository
```

### Gradle Semanatic Build Versioning Plugin

This plugin (see [here](https://github.com/vivin/gradle-semantic-build-versioning)) provides support for
automatic semantic versioning of builds based off git commit information.

GME supports handling this plugin through the CLI tool *only*. If this plugin is detected then GME will first
establish the actual version via the [printVersion](https://github.com/vivin/gradle-semantic-build-versioning#printversion)
command, inserting that into the `gradle.properties`. Finally it will remove the plugin to ensure the version
is kept static.
