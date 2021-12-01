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
