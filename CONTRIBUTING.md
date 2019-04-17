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