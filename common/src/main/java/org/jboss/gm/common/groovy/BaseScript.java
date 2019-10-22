package org.jboss.gm.common.groovy;

import java.io.File;
import java.util.Properties;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.groovy.GradleBaseScript;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.api.Project;
import org.jboss.gm.common.model.ManipulationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class that should be used by developers wishing to implement groovy scripts
 * for GME.
 */
public abstract class BaseScript extends GradleBaseScript {

    // Explicitly using LoggerFactory not GMLogger to avoid NoClassDefFound issues with cli jar.
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private ManipulationModel model;

    private Project rootProject;

    private File basedir;

    private Properties properties;

    private InvocationStage stage;

    /**
     * Return the current Project
     * 
     * @return a {@link Project} instance.
     */
    @Override
    public Project getProject() {
        if (stage == InvocationStage.FIRST) {
            logger.error("getProject unsupported for InvocationStage FIRST");
            throw new ManipulationUncheckedException("getProject is not supported for Groovy in initial stage.");
        }
        return rootProject;
    }

    /**
     * Get the current ManipulationModel instance for remote artifact resolving.
     * 
     * @return a {@link ManipulationModel} reference.
     */
    @Override
    public ManipulationModel getModel() {
        if (stage == InvocationStage.FIRST) {
            logger.error("getModel unsupported for InvocationStage FIRST");
            throw new ManipulationUncheckedException("Model is not supported for Groovy in initial stage.");
        }
        return model;
    }

    /**
     * Get the working directory (the execution root).
     * 
     * @return a {@link java.io.File} reference.
     */
    @Override
    public File getBaseDir() {
        return basedir;
    }

    /**
     * Get the user properties
     * 
     * @return a {@link java.util.Properties} reference.
     */
    public Properties getUserProperties() {
        return properties;
    }

    /**
     * Get the current stage
     * 
     * @return a {@link InvocationStage} reference.
     */
    @Override
    public InvocationStage getInvocationStage() {
        return stage;
    }

    /**
     * Internal use only - the org.jboss.gm.analyzer.alignment.AlignmentTask uses this to
     * initialise the values
     * 
     * @param stage the current invocation stage
     * @param rootDir the root directory of the project.
     * @param properties the current configuration properties
     * @param rootProject Current project
     * @param model the current aligned model.
     */
    public void setValues(InvocationStage stage, File rootDir, Properties properties, Project rootProject,
            ManipulationModel model) {
        this.stage = stage;
        this.rootProject = rootProject;
        this.model = model;
        this.basedir = rootDir;
        this.properties = properties;

        logger.info("Injecting values for stage {}. Project is {} with basedir {}", stage, rootProject, rootDir);
    }

}
