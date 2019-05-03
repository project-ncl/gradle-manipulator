package org.jboss.gm.common;

import static org.junit.Assert.assertEquals;

import org.aeonbits.owner.ConfigFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;

public class ConfigurationTest {

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void verifySystem() {
        System.setProperty("TEST", "VALUE");

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals("VALUE", c.getProperty("TEST"));
    }
}
