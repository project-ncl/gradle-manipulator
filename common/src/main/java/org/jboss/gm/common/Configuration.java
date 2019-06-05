package org.jboss.gm.common;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Converter;
import org.commonjava.maven.ext.core.state.DependencyState.DependencyPrecedence;

/**
 * This class is used to hold all configuration values for the two plugins. The naming scheme of
 * the configuration variables has been chosen to match the
 * <a href=https://release-engineering.github.io/pom-manipulation-ext/#feature-guide>PME</a> naming.
 */
@Sources({ "system:properties", "system:env" })
public interface Configuration extends Accessible {

    /**
     * Cache is indexed to object instance : Properties - while in production
     * usage there should only be one instance, using this allows for easier testing.
     */
    Map<Configuration, Properties> properties = new HashMap<>();

    String DA = "restURL";

    /**
     * This method simply encapsulates the {@link Accessible#fill(Map)} in order to
     * cache the value to avoid repeated calls.
     *
     * @return Properties object containing all values.
     */
    default Properties getProperties() {
        return properties.computeIfAbsent(this, key -> {
            Properties p = new Properties();
            fill(p);
            return p;
        });
    }

    @Key(DA)
    String daEndpoint();

    @Key("AProxDeployUrl")
    String deployUrl();

    @Key("deployPlugin")
    String deployPlugin();

    @Key("accessToken")
    String accessToken();

    @Key("versionIncrementalSuffix")
    @DefaultValue("redhat")
    String versionIncrementalSuffix();

    @Key("versionIncrementalSuffixPadding")
    @DefaultValue("5")
    int versionIncrementalSuffixPadding();

    @Key("versionModification")
    @DefaultValue("true")
    boolean versionModificationEnabled();

    @Key("restRepositoryGroup")
    String restRepositoryGroup();

    @Key("restMaxSize")
    @DefaultValue("0")
    int restMaxSize();

    @Key("log-context")
    String logContext();

    @Key("ignoreUnresolvableDependencies")
    @DefaultValue("false")
    boolean ignoreUnresolvableDependencies();

    /**
     * This value is used to represent the dependency configuration. While PME supports
     * BOM and REST configs ; currently within Gradle only REST is supported. If this
     * value is set to "" (or "NONE")
     */
    @Key("dependencySource")
    @ConverterClass(DependencyConverter.class)
    @DefaultValue("REST")
    DependencyPrecedence dependencyConfiguration();

    /**
     * Path to a file where project's artifact repositories will be exported in the maven settings format.
     *
     * PNC will use this file to configure repository proxying.
     */
    @Key("repoRemovalBackup")
    @DefaultValue("repositories-backup.xml")
    String repositoriesFile();

    class DependencyConverter implements Converter<DependencyPrecedence> {
        /**
         * Converts the given input into an Object of type T.
         * If the method returns null, null will be returned by the Config object.
         * The converter is instantiated for every call, so it shouldn't have any internal state.
         *
         * @param method the method invoked on the <tt>{@link Config} object</tt>
         * @param input the property value specified as input text to be converted to the T return type
         * @return the object of type T converted from the input string.
         * @since 1.0.4
         */
        @Override
        public DependencyPrecedence convert(Method method, String input) {
            if (isEmpty(input)) {
                return DependencyPrecedence.NONE;
            }
            return DependencyPrecedence.valueOf(input.toUpperCase());
        }
    }
}
