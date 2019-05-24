---
title: "Artifact Publishing"
---

* Contents
{:toc}

### Overview

The `manipulation` plugin tries to detect a publishing plugin used by a project. It currently recognizes two plugins:

* the legacy `maven` plugin,
* the `maven-publish` plugin.

If one of the above plugins is detected, it is configured to support publication of project artifacts in PNC
environment.

Following properties need to be given either as system properties or as environment variables:

* `AProxDeployUrl` - deployment repository URL,
* `accessToken` - authorization access token (optional) 

In PNC above would be already defined as environment variables.

#### Legacy Maven Plugin

If the project uses the legacy `maven` plugin, use the `uploadArchives` task to publish artifacts.

Following changes are made automatically: 

* custom `Action` is added to all maven repositories of all `Upload` tasks, which overrides versions in generated 
  POM files,
* if `uploadArchives` task doesn't exist, it is created,
* `uploadArchives` task is made to depend on `install` task, so that the former is able to consume `pom.xml` generated 
  by the latter,
* publishing repository is added.

Loosely equivalent groovy configuration would be:

```groovy
tasks.withType(Upload) {
  repositories.withType(MavenResolver) {
    pom.withXml(new PomTransfomer(...))
  }
}

uploadArchives {
  repositories {
    maven {
      url = System.getProperty('AProxDeployUrl')
      credentials(HttpHeaderCredentials) {
        name = "Authorization"
        value = "Bearer " + System.getProperty('accessToken')
      }
      authentication {
        header(HttpHeaderAuthentication)
      }
    }
  }
}

```

#### Maven-publish Plugin

If the project uses `maven-publish` plugin, use the `publish` task to publish artifacts.

Following changes are made automatically:

* new `MavenPublication` is created,
* custom `Action` is added to all publications of type `MavenPublications`, which overrides versions in generated pom files,
* publishing repository is added.

Loosely equivalent groovy configuration would be:

```groovy
publishing {
  publications {
    mavenJava(MavenPublication) {
      from components.java
      pom.withXml(new PomTransfomer(...))
    }
  }
}

publishing {
  repositories {
    maven {
      url = System.getProperty('AProxDeployUrl')
      credentials(HttpHeaderCredentials) {
        name = "Authorization"
        value = "Bearer " + System.getProperty('accessToken')
      }
      authentication {
        header(HttpHeaderAuthentication)
      }
    }
  }
}
```

#### Examples

On local machine, you can try following to trigger publication.

For legacy `maven` plugin:

```./gradlew uploadArchives -DAProxDeployUrl=file:///tmp/repo```

For `maven-publish` plugin:

```./gradlew publish -DAProxDeployUrl=file:///tmp/repo```

Project artifacts should be published in `/tmp/repo` where you can review them.

In PNC, `AProxDeployUrl` is already defined as environment variable, so it can be omitted.