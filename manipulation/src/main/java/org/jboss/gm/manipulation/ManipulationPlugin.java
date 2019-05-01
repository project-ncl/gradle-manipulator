package org.jboss.gm.manipulation;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.jboss.gm.common.alignment.ManipulationUtils.getCurrentManipulationModel;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.MavenPlugin;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.alignment.ManifestUtils;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.MavenPublicationRepositoryAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.ProjectChangeVersionAction;
import org.jboss.gm.manipulation.actions.PublishingArtifactsAction;
import org.jboss.gm.manipulation.actions.PublishingPomTransformerAction;
import org.jboss.gm.manipulation.actions.PublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManipulationPlugin implements Plugin<Project> {

    private static final String LEGACY_MAVEN_PLUGIN = "maven";
    private static final String MAVEN_PUBLISH_PLUGIN = "maven-publish";

    static {
        System.out.println("Injecting ManipulationPlugin ; version " + ManifestUtils.getManifestInformation());
    }

    private final Logger logger = LoggerFactory.getLogger(ManipulationPlugin.class);
    
    @Override
    public void apply(Project project) {
        // get the previously performed alignment
        final ManipulationModel alignmentModel = getCurrentManipulationModel(project.getRootDir());
        final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project.getName());

        // add actions to manipulate project
        project.afterEvaluate(new ProjectChangeVersionAction(correspondingModule));
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule));
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));

        configurePublishingTask(project, correspondingModule);
    }

    /**
     * TODO: add functional tests for publishing
     */
    private void configurePublishingTask(Project project, ManipulationModel correspondingModule) {
        project.afterEvaluate(evaluatedProject -> {
            // we need to determine which plugin to configure for publication

            // first, let the choice be enforced via a system property
            Configuration config = ConfigCache.getOrCreate(Configuration.class);
            String deployPlugin = config.deployPlugin();
            if (!isEmpty(deployPlugin)) {
                logger.info("Enforcing artifact deployment plugin `{}`.", deployPlugin);
            }

            // if enforced plugin is not configured in the project, apply it
            if (LEGACY_MAVEN_PLUGIN.equals(deployPlugin)
                    && !evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                evaluatedProject.getPluginManager().apply(MavenPlugin.class);
            } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)
                    && !evaluatedProject.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN)) {
                evaluatedProject.getPluginManager().apply(MavenPublishPlugin.class);
                evaluatedProject.afterEvaluate(new PublishingArtifactsAction());
            } else if (deployPlugin != null) {
                throw new InvalidUserDataException("Invalid publishing plugin preference: " + deployPlugin);
            }

            // if no plugin is enforced by configuration, look at which plugin is used by the project
            if (isEmpty(deployPlugin)) {
                if (evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                    deployPlugin = LEGACY_MAVEN_PLUGIN;
                } else if (evaluatedProject.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN)) {
                    deployPlugin = MAVEN_PUBLISH_PLUGIN;
                }
            }

            if (LEGACY_MAVEN_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring `maven` plugin");
                evaluatedProject.afterEvaluate(new UploadTaskTransformerAction(correspondingModule));
                evaluatedProject.afterEvaluate(new MavenPublicationRepositoryAction());
            } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring `maven-publish` plugin");
                evaluatedProject.afterEvaluate(new PublishingRepositoryAction());
                evaluatedProject.afterEvaluate(new PublishingPomTransformerAction(correspondingModule));
            } else {
                logger.warn("No publishing plugin was configured!");
            }
        });
    }
}
