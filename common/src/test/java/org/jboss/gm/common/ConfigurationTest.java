package org.jboss.gm.common;

import static org.junit.Assert.assertEquals;

import org.aeonbits.owner.ConfigFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;

public class ConfigurationTest {

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void verifySystem1() {
        System.setProperty("TEST", "VALUE");

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals("VALUE", c.getProperty("TEST"));
    }

    @Test
    public void verifySystem2() {
        String deploy = "http://indy-stable-next.newcastle-devel.svc.cluster.local";
        System.setProperty("AProxDeployUrl", deploy);

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals(deploy, c.deployUrl());
    }

    @Test
    public void verifyEnv() {

        String deploy = "http://indy-stable-next.newcastle-devel.svc.cluster.local";
        environmentVariables.set("AProxDeployUrl", deploy);

        Configuration c = ConfigFactory.create(Configuration.class);

        assertEquals(deploy, c.deployUrl());
    }
}
