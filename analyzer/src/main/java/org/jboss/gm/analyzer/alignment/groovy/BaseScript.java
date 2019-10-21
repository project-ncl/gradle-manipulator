package org.jboss.gm.analyzer.alignment.groovy;

import java.io.File;
import java.util.Properties;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.groovy.GradleBaseScript;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Abstract class that should be used by developers wishing to implement groovy scripts
 * for GME.
 */
public abstract class BaseScript extends GradleBaseScript {
    protected final Logger logger = GMLogger.getLogger(getClass());

    private ManipulationModel model;

    private Project rootProject;

    private ProjectVersionRef gav;

    private File basedir;

    private Properties userProperties;

    private InvocationStage stage;

    /**
     * Return the current Project
     * 
     * @return a {@link Project} instance.
     */
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
    public ManipulationModel getModel() {
        if (stage == InvocationStage.FIRST) {
            logger.error("getModel unsupported for InvocationStage FIRST");
            throw new ManipulationUncheckedException("Model is not supported for Groovy in initial stage.");
        }
        return model;
    }

    /**
     * Obtain the GAV of the current project
     * 
     * @return a {@link org.commonjava.maven.atlas.ident.ref.ProjectVersionRef}
     */
    public ProjectVersionRef getGAV() {
        return gav;
    }

    /**
     * Get the working directory (the execution root).
     * 
     * @return a {@link java.io.File} reference.
     */
    public File getBaseDir() {
        return basedir;
    }

    /**
     * Get the user properties
     * 
     * @return a {@link java.util.Properties} reference.
     */
    public Properties getUserProperties() {
        if ( stage == InvocationStage.FIRST ) {
            logger.error("getUserProperties unsupported for InvocationStage FIRST");
            throw new ManipulationUncheckedException("NYI.");
        }
        return userProperties;
    }

    /**
     * Get the current stage
     * 
     * @return a {@link InvocationStage} reference.
     */
    public InvocationStage getInvocationStage() {
        return stage;
    }

    /**
     * Internal use only - the {@link org.jboss.gm.analyzer.alignment.AlignmentTask} uses this to
     * initialise the values
     *
     * @param stage the current invocation stage
     * @param rootDir the root directory of the project.
     * @param rootProject Current project
     * @param model the current aligned model.
     */
    public void setValues(InvocationStage stage, File rootDir, Project rootProject, ManipulationModel model) {
        this.stage = stage;
        this.rootProject = rootProject;
        this.model = model;
        this.basedir = rootDir;

        // TODO: Remained of initialization.
        
        logger.info("Injecting values. Project is " + rootProject + " with basedir " + rootProject.getRootDir());
    }

}
