package org.jboss.gm.common.groovy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import lombok.Getter;

import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.groovy.GradleBaseScript;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.commonjava.maven.ext.io.FileIO;
import org.commonjava.maven.ext.io.resolver.GalleyInfrastructure;
import org.commonjava.maven.ext.io.rest.Translator;
import org.gradle.api.Project;
import org.jboss.gm.common.model.ManipulationModel;
import org.slf4j.Logger;

/**
 * Abstract class that should be used by developers wishing to implement groovy scripts
 * for GME.
 */
public abstract class BaseScript extends GradleBaseScript {

    // Explicitly using LoggerFactory not GMLogger to avoid NoClassDefFound issues with cli jar.
    @Getter
    protected Logger logger;

    private ManipulationModel model;

    private Project rootProject;

    private File basedir;

    private Properties properties;

    private InvocationStage stage;

    private FileIO fileIO;

    private Translator restAPI;

    /**
     * Return the current Project
     *
     * @return a {@link Project} instance.
     */
    @Override
    public Project getProject() {
        if (stage == InvocationStage.PREPARSE || stage == InvocationStage.FIRST) {
            logger.error("getProject unsupported for InvocationStage {}", stage.name());
            throw new ManipulationUncheckedException(
                    "Getting the project is not supported for Groovy in stage " + stage.name());
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
        if (stage == InvocationStage.PREPARSE || stage == InvocationStage.FIRST) {
            logger.error("getModel unsupported for InvocationStage {}", stage.name());
            throw new ManipulationUncheckedException("Getting the model is not supported for Groovy in stage " + stage.name());
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

    @Override
    public FileIO getFileIO() {
        return fileIO;
    }

    @Override
    public Translator getRESTAPI() {
        return restAPI;
    }

    /**
     * Internal use only - the org.jboss.gm.analyzer.alignment.AlignmentTask uses this to
     * initialise the values
     *
     * @param logger the logger
     * @param stage the current invocation stage
     * @param rootDir the root directory of the project.
     * @param properties the current configuration properties
     * @param restAPI the REST API Translator interface
     * @param rootProject Current project
     * @param model the current aligned model.
     */
    public void setValues(Logger logger, InvocationStage stage, File rootDir, Properties properties, Translator restAPI,
            Project rootProject, ManipulationModel model) {
        this.logger = logger;
        this.stage = stage;
        this.rootProject = rootProject;
        this.model = model;
        this.basedir = rootDir;
        this.properties = properties;
        this.restAPI = restAPI;

        File repoCache;
        try {
            if (rootProject == null) {
                repoCache = Files.createTempDirectory("repo-cache-").toFile();
            } else {
                repoCache = new File(rootProject.getBuildDir(), "repo-cache-");
                //noinspection ResultOfMethodCallIgnored
                repoCache.mkdirs();
            }
            fileIO = new FileIO(new GalleyInfrastructure(null, null).init(repoCache));

        } catch (IOException | ManipulationException e) {
            throw new ManipulationUncheckedException("Unable to create FileIO temporary directory", e);
        }

        logger.info("Injecting values for stage {}. Project is {} with basedir {}", stage, rootProject, rootDir);
    }
}
