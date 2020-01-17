package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.groovy.InvocationPoint;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.api.Project;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.groovy.BaseScript;
import org.jboss.gm.common.model.ManipulationModel;
import org.slf4j.Logger;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

@UtilityClass
public class GroovyUtils {

    /**
     *
     * @param logger The logger to use. Note: this is specifically using SLF4J logging to allow interaction with the CLI.
     * @param targetStage The stage we are running
     * @param target The target directory to run against.
     * @param configuration The current configuration object.
     * @param rootProject The current root project ; may be null.
     * @param alignmentModel The current model ; may be null.
     * @throws ManipulationException if an error occurs.
     */
    public static void runCustomGroovyScript(Logger logger, InvocationStage targetStage, File target,
            Configuration configuration, Project rootProject,
            ManipulationModel alignmentModel) throws ManipulationException {

        final List<File> groovyFiles = new ArrayList<>();
        final String[] scripts = configuration.groovyScripts();

        if (scripts != null) {
            int i = 0;
            for (String script : scripts) {
                logger.info("Attempting to read URL {} ", script);
                try {
                    File remote = File.createTempFile("gme-" + i, ".groovy");
                    remote.deleteOnExit();
                    FileUtils.copyURLToFile(new URL(script), remote);
                    groovyFiles.add(remote);
                } catch (IOException e) {
                    logger.error("Ignoring script {} as unable to locate it.", script);
                    logger.debug("Problem with script URL is", e);
                }
            }
        }

        for (File scriptFile : groovyFiles) {
            final Binding binding = new Binding();
            // We use the current class' classloader so the script has access to this plugin's API and the
            // groovy API.
            final GroovyShell groovyShell = new GroovyShell(logger.getClass().getClassLoader(), binding);
            final Script script;
            try {
                script = groovyShell.parse(scriptFile);
            } catch (IOException e) {
                throw new ManipulationException("Unable to parse script");
            }
            final InvocationStage stage;
            final InvocationPoint invocationPoint = script.getClass().getAnnotation(InvocationPoint.class);

            logger.info("For target stage {} attempting to invoke groovy script {} ", targetStage, scriptFile);
            if (invocationPoint != null) {
                logger.debug("InvocationPoint is {}", invocationPoint.invocationPoint().toString());
                stage = invocationPoint.invocationPoint();
            } else {
                throw new ManipulationException("Mandatory annotation '@InvocationPoint(invocationPoint = ' not declared");
            }

            if (targetStage == stage || InvocationStage.BOTH == stage) {
                // Inject the values via a new BaseScript so user's can have completion.
                if (script instanceof BaseScript) {
                    ((BaseScript) script).setValues(stage, target, configuration.getProperties(), rootProject,
                            alignmentModel);
                } else {
                    throw new ManipulationException("Cannot cast " + script + " to a BaseScript to set values.");
                }
                try {
                    logger.info("Executing {} on {} at invocation point {}", script, rootProject, stage);

                    script.run();
                } catch (Exception e) {
                    //noinspection ConstantConditions
                    if (e instanceof ManipulationException) {
                        throw ((ManipulationException) e);
                    } else {
                        throw new ManipulationException("Problem running script", e);
                    }
                }
            } else {
                logger.debug("Ignoring script {} as invocation point {} does not match.", script, stage);
            }
        }
    }
}
