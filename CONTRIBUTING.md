
# Development

Contributions to the project are very welcome! Please submit pull requests with any changes (preferably with tests).

 * [Documentation](#documentation)
 * [IDE Config and Code Style](#ide-config-and-code-style)
    * [Eclipse Setup](#eclipse-setup)
    * [IDEA Setup](#idea-setup)
 * [Code Recommendations](#code-recommendations)
    * [Exceptions](#exceptions)
 * [Releasing](#releasing)
    * [Prerequisites](#prerequisites)
    * [Release command](#release-command)
    * [Publishing artifacts locally](#publishing-artifacts-locally)



### Documentation

The documentation for the project can be found [here](https://project-ncl.github.io/gradle-manipulator/). In order to edit the website checkout the `gh-pages` branch. It is possible to use Jekyll (https://help.github.com/articles/using-jekyll-with-pages) to preview the changes. Jekyll can be run with `jekyll serve --watch -V`

### IDE Config and Code Style

This project has a strictly enforced code style. Code formatting is done by the Eclipse code formatter, using the config files
found in the `ide-config` directory. By default when you run `./gradlew build` the code will be formatted automatically.

#### Eclipse Setup

Open the *Preferences* window, and then navigate to _Java_ -> _Code Style_ -> _Formatter_. Click _Import_ and then
select the `eclipse-format.xml` file in the `ide-config` directory.

Next navigate to _Java_ -> _Code Style_ -> _Organize Imports_. Click _Import_ and select the `eclipse.importorder` file.

#### IDEA Setup

Open the _Preferences_ window, navigate to _Plugins_ and install the [Eclipse Code Formatter Plugin](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter).

Restart you IDE, open the *Preferences* window again and navigate to _Other Settings_ -> _Eclipse Code Formatter_.

Select _Use the Eclipse Code Formatter_, then change the _Eclipse Java Formatter Config File_ to point to the
`eclipse-format.xml` file in the `ide-config` directory. Make sure the _Optimize Imports_ box is ticked, and
select the `eclipse.importorder` file as the import order config file.

### Code Recommendations

#### Logging

The tooling overrides the logging to support integration into ProjectNCL. Therefore to retrieve a standard Gradle logger instance do :

```java
     org.gradle.api.logging.Logger logger = org.jboss.gm.common.logging.GMLogger.getLogger(getClass());
```
The Gradle logger interface extends `org.slf4j.Logger`. It is recommended to use `info` and `debug` categories as appropriate.

#### Exceptions

* Internal code may use checked exceptions such as `ManipulationException`.
* All external invocation points cannot throw a checked exception so must use an unchecked exception (e.g. `ManipulationUncheckedException`.
* The `org.gradle.api.InvalidUserDataException` can be used for configuration errors.
* Avoid throwing `RuntimeException` ; rather, use a more explicit exception.

#### Gradle Test Runner

The [test runner](https://docs.gradle.org/current/userguide/test_kit.html) may be used for functional testing. Note that it is
recommended that `withDebug(true)` is **not** used in production but just enabled as required. This is because debug runs
in-process which can lead to some strange side affects.

To pass specific property / environment variables through the following pattern should be followed (which will also work when
using single-test debugging):
```java
    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

......
        System.setProperty("AProxDeployUrl", "file://" + publishDirectory.toString());

        final BuildResult buildResult = TestUtils.createGradleRunner ()
                .withProjectDir(simpleProjectRoot)
                .withArguments("--info", "publish")
                .build();
     ....
```
Note that instead of calling `GradleRunner.create` the `TestUtils` class is used instead which passes on the extra system 
properties.

### Releasing

The project has been configured to release both plugins to the Gradle Portal and to release to Maven Central. 

<!-- Using diff syntax to highlight the warning in red -->
```diff
- Before running the release notify the team to lock down the repository until the release is finished. -
```

It uses the [gradle-release](https://github.com/researchgate/gradle-release) plugin to simulate a similar process to the Maven release plugin - it can increment the version, add tags, push the changes, etc. It supplies a hook task (`afterReleaseBuild`) that can be used to ensure that tasks after the release version has been changed - we have configured it as follows:

```
tasks.afterReleaseBuild { 
    dependsOn(":analyzer:publish", ":manipulation:publish", 
              ":analyzer:publishPlugins", ":manipulation:publishPlugins") }
```

The `publish` pushes to Sonatype staging while the `publishPlugins` pushes to the Gradle Plugin Portal.

#### Prerequisites

* Sign up for a Gradle account (see details [here](https://guides.gradle.org/publishing-plugins-to-gradle-plugin-portal/#create_an_account_on_the_gradle_plugin_portal))
* Make sure you can push changes to Maven Central 
* Create or update the required `$HOME/.gradle/gradle.properties` locally with data from the API key (which can be found in your gradle account)

It should look something like this:

```
gradle.publish.key=key
gradle.publish.secret=secret
```

* Update `$HOME/.gradle/gradle.properties` to contain the necessary configuration for publishing to Maven Central. Something like:

```
signing.gnupg.keyName=someKey
signing.passphrase=pass
```   

Note: By default the signing is configured to use GPG. It will automatically look for the `gpg2` executable. On Fedora systems this is normally a symbolic link e.g.
```
    /usr/bin/gpg*
    /usr/bin/gpg2 -> gpg*
```
If this does not exist on your system then you may need to add a line like `signing.gnupg.executable=gpg` to the properties file.

<br>

The configuration will also read your `$HOME/.m2/settings.xml` for a username/password associated with `sonatype-nexus-staging`. 


See [this](https://docs.gradle.org/current/userguide/signing_plugin.html) for more details

#### Release command

The plugins can be released using the following command (from the master branch of the repository):


	./gradlew clean release -Drelease=true
	
The command will both publish the plugin to the Gradle Plugin Portal and to Maven Central.

Note: It is **very** important to execute this exact command when releasing. Adding other tasks can cause the release to fail,
or even worse leave the release in an inconsistent state. 
	
#### Publishing artifacts locally

Both plugins can be published to maven local to make it easier to consume them in projects. The command to do so is:

	./gradlew publishToMavenLocal
	
To change the version that will be deployed just add `-Pversion=whatever`.	
 	     
