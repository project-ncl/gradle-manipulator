package org.jboss.gm.analyzer.alignment.groovy;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;

import groovy.lang.Script;

/**
 * Abstract class that should be used by developers wishing to implement groovy scripts
 * for GME.
 */
public abstract class GMEBaseScript extends Script {
    protected final Logger logger = GMLogger.getLogger(getClass());

    private ManipulationModel model;

    private Project rootProject;

    /**
     * Return the current Project
     * 
     * @return a {@link Project} instance.
     */
    public Project getProject() {
        return rootProject;
    }

    /**
     * Get the current ManipulationModel instance for remote artifact resolving.
     * 
     * @return a {@link ManipulationModel} reference.
     */
    public ManipulationModel getModel() {
        return model;
    }

    /**
     * Internal use only - the {@link org.jboss.gm.analyzer.alignment.AlignmentTask} uses this to
     * initialise the values
     * 
     * @param rootProject Current project
     * @param model the current aligned model.
     */
    public void setValues(Project rootProject, ManipulationModel model) {
        this.rootProject = rootProject;
        this.model = model;

        logger.info("Injecting values. Project is " + rootProject + " with basedir " + rootProject.getRootDir());
    }

}
