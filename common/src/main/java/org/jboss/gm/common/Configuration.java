package org.jboss.gm.common;

import org.aeonbits.owner.Accessible;
import org.aeonbits.owner.Config.Sources;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Sources({ "system:properties", "system:env" })
public interface Configuration extends Accessible {
    Map<Configuration, Properties> properties = new HashMap<>();
    String DA = "restURL";

    /**
     * This method simply encapsulates the {@link Accessible#fill(Map)} in order to
     * cache the value to avoid repeated calls.
     *
     * @return Properties object containing all values.
     */
    default Properties getProperties() {
        Properties result = properties.computeIfAbsent(this, key -> {
            Properties p = new Properties();
            fill(p);
            return p;
        });
        return result;
    }

    @Key(DA)
    String daEndpoint();

    @Key("versionIncrementalSuffix")
    @DefaultValue("redhat")
    String versionIncrementalSuffix();

    @Key("versionIncrementalSuffixPadding")
    @DefaultValue("5")
    int versionIncrementalSuffixPadding();

}
