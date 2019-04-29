package org.jboss.gm.analyzer.alignment;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.SystemConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * This is what {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME} task uses in order to retrieve the value of the
 * various configuration options it needs.
 * The configuration is backed by Apache Commons Config 2 and uses three sources (of descending order of precedence)
 * <ul>
 * <li>Environment Variables</li>
 * <li>System Properties</li>
 * <li>analyzer-defaults.properties</li>
 * </ul>
 *
 */
public final class ConfigurationFactory {

    private ConfigurationFactory() {
    }

    // TODO: Prevent duplicate values being stored
    public static Configuration getConfiguration() {
        CompositeConfiguration configuration = new CompositeConfiguration();
        configuration.addConfiguration(new ConvertingEnvironmentConfiguration());
        configuration.addConfiguration(new SystemConfiguration());
        configuration.addConfiguration(propertiesConfiguration());

        return configuration;
    }

    private static Configuration propertiesConfiguration() {
        final Parameters params = new Parameters();
        final FileBasedConfigurationBuilder<FileBasedConfiguration> builder = new FileBasedConfigurationBuilder<FileBasedConfiguration>(
                PropertiesConfiguration.class)
                        .configure(params.properties()
                                .setFileName("analyzer-defaults.properties"));
        try {
            return builder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Provides simplistic mapping of env variables to property names.
     * The mapping currently only lowercases the env vars and replaces '_' with '.'
     *
     * TODO: this will definitely need to be augmented if we start using not trivial property lookup
     */
    private static class ConvertingEnvironmentConfiguration extends MapConfiguration {

        ConvertingEnvironmentConfiguration() {
            super(getEnvWithConvertedKeys());
        }

        private static Map<String, String> getEnvWithConvertedKeys() {
            final Map<String, String> env = System.getenv();
            final Map<String, String> convertedKeysEnv = new HashMap<>(env.size());
            for (Map.Entry<String, String> envEntry : env.entrySet()) {
                convertedKeysEnv.put(convertKey(envEntry.getKey()), envEntry.getValue());
            }
            return convertedKeysEnv;
        }

        private static String convertKey(String key) {
            return key.toLowerCase().replace('_', '.');
        }

    }
}
