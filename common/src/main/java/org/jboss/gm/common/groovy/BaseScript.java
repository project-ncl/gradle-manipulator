package org.jboss.gm.common.groovy;

/**
 * Abstract class that should be used by developers wishing to implement groovy scripts
 * for GME.
 */
public abstract class BaseScript extends org.jboss.pnc.gradlemanipulator.common.groovy.BaseScript {
    {
        println("Deprecated Groovy API - switch to importing org.jboss.pnc.gradlemanipulator");
    }
}
