package org.jboss.gm.analyzer.alignment;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.SystemConfiguration;

public final class ConfigurationFactory {

    private static Configuration configuration;

    private ConfigurationFactory() {
    }

    public static Configuration getConfiguration() {
        if (configuration == null) {
            //TODO: do we need to have a properties file configuration?
            configuration = new SystemConfiguration();
        }
        return configuration;
    }
}
