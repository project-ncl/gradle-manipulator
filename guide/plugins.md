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

| Plugin ID                                           | Configuration Block         | Tasks                                                               |
|-----------------------------------------------------|-----------------------------|---------------------------------------------------------------------|
| `com.github.ben-manes.versions`                     | `dependencyUpdates`         | `DependencyUpdatesTask`                                             |
| `com.github.burrunan.s3-build-cache`                | `buildCache`                |                                                                     |
| `com.gradle.common-custom-user-data-gradle-plugin`  |                             |                                                                     |
| `de.marcphilipp.nexus-publish`                      | `nexusPublishing`           |                                                                     |
| `gradle-enterprise`                                 | `gradleEnterprise`          |                                                                     |
| `com.gradle.enterprise`                             | `gradleEnterprise`          |                                                                     |
| `com.gradle.common-custom-user-data-gradle-plugin`  |                             |                                                                     |
| `io.codearte.nexus-staging`                         | `nexusStaging`              | `closeRepository`, `releaseRepository`, `closeAndReleaseRepository` |
| `io.github.gradle-nexus.publish-plugin`             | `nexusPublishing`           | `publishToSonatype`                                                 |
| `io.spring.ge.conventions`                          |                             |                                                                     |
| `nebula.publish-verification`                       | `nebulaPublishVerification` |                                                                     |
| `signing`                                           | `signing`                   |                                                                     |
| `net.vivin.gradle-semantic-build-versioning`        | `preRelease`                |                                                                     |

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

#### Shortcuts

Two special shortcuts are available to support removing collections of plugins:

* `ALL` represents removing all supported plugins.
* `REC` / `RECOMMENDED` represents removing the recommended list. This is currently the same as
  above but doesn't include the Signing plugin.

<table bgcolor="#ffff00">
<tr>
<td>
    <code>REC</code> is available from version 3.11
</td>
</tr>
</table>



### Gradle Semantic Build Versioning Plugin

This plugin (see [here](https://github.com/vivin/gradle-semantic-build-versioning)) provides support for
automatic semantic versioning of builds based off git commit information.

GME supports handling this plugin through the CLI tool *only*. If this plugin is detected then GME will first
establish the actual version via the [printVersion](https://github.com/vivin/gradle-semantic-build-versioning#printversion)
command, inserting that into the `gradle.properties`. Finally it will remove the plugin to ensure the version
is kept static.


### Dokka Plugin

Special handling has been included for the [dokka](https://github.com/Kotlin/dokka/) plugin in
order to inject the correct settings for earlier versions which did not respect proxy settings.

It is possible to disable this through the configuration key `dokkaPlugin` (default: true)

<table bgcolor="#ffff00">
<tr>
<td>
    <code>dokkaPlugin</code> is available from version 3.9
</td>
</tr>
</table>

#### [Gradle Maven Publish Plugin](https://github.com/vanniktech/gradle-maven-publish-plugin)

The gradle-maven-publish-plugin implicitly injects other plugins such as the signing and dokka
plugins. In version 0.8.0 it injects 0.9.17 of the Dokka plugin. Currently GME is not able to change
this injected version to 0.9.18 (unlike a directly included 0.9.17 version) and therefore this can
lead to problems with the GME injected code. It is currently recommended that the version is changed
from 0.8.0 to >= 0.9.0 which injects the dokka plugin at version 0.9.18.

### Publish Plugin Hook

Certain project builds don't apply the publish plugin directly (be it the legacy or current one); instead they implement their own 'build plugin' (e.g. within `buildSrc`) that itself then applies plugins. This can lead to the situation where this custom plugin is applied and actioned after the GME tooling plugin which therefore does not detect any publishing plugins. It is possible to list those custom plugins as 'hooks' that GME will detect, and attempt to customise the publishing again. It is a comma separated list with a single default entry of `elasticsearch.esplugin`.
