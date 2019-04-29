package org.jboss.gm.common;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.Converter;
import org.commonjava.maven.ext.core.state.DependencyState.DependencyPrecedence;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.commons.lang3.StringUtils.isEmpty;

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

    @Key("accessToken")
    String accessToken();

    @Key("versionIncrementalSuffix")
    @DefaultValue("redhat")
    String versionIncrementalSuffix();

    @Key("versionIncrementalSuffixPadding")
    @DefaultValue("5")
    int versionIncrementalSuffixPadding();

    @Key("dependencySource")
    @ConverterClass(DependencyConverter.class)
    @DefaultValue("REST")
    DependencyPrecedence dependencyConfiguration();

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
            return DependencyPrecedence.valueOf(input);
        }
    }
}
