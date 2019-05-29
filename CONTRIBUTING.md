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

### Recommendations

#### Exceptions

* Internal code may use checked exceptions such as `ManipulationException`.
* All external invocation points cannot throw a checked exception so must use an unchecked exception (e.g. `ManipulationUncheckedException`.
* The `org.gradle.api.InvalidUserDataException` can be used for configuration errors.
* Avoid throwing `RuntimeException` ; rather, use a more explicit exception.

### Releasing

The project has been configured to release both plugins to the Gradle Portal.

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
signing.gnupg.executable=gpg
signing.gnupg.keyName=someKey
signing.passphrase=pass
```   

See [this](https://docs.gradle.org/current/userguide/signing_plugin.html) for more details

#### Release command

The plugins can be released using the following command:


	./gradlew clean release -Drelease=true
	
The command will both publish the plugin to the Gradle Plugin Portal and to Maven Central.
	
#### Publishing artifacts locally

Both plugins can be published to maven local to make it easier to consume them in projects. The command to do so is:

	./gradlew publishToMavenLocal
	
To change the version that will be deployed just add `-Pversion=whatever`.	
 	     
### More documentation

More documentation for the project can be found [here](https://project-ncl.github.io/gradle-manipulator/).
