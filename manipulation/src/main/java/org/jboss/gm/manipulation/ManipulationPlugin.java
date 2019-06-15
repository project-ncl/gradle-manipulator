package org.jboss.gm.manipulation;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.lang.reflect.Method;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.MavenPlugin;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.ManifestUtils;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.MavenPublicationRepositoryAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.PublishingArtifactsAction;
import org.jboss.gm.manipulation.actions.PublishingPomTransformerAction;
import org.jboss.gm.manipulation.actions.PublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.ResolvedDependenciesRepository;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
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
        final ManipulationModel alignmentModel = ManipulationIO.readManipulationModel(project.getRootDir());
        final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project.getName());

        // we need to change the project version early so various tasks that ready early and create other vars based on it
        // (like the zip tasks) can use the correct version
        logger.info("Updating project version {} to {} ", project.getVersion(), alignmentModel.getVersion());
        project.setVersion(alignmentModel.getVersion());

        final ResolvedDependenciesRepository resolvedDependenciesRepository = new ResolvedDependenciesRepository();

        project.afterEvaluate(p -> {

            // This double version set is required - sometimes other plugins seem to override the version we set initially.
            // We need to set it at the start as other plugins also require it there. Hence this belt and braces approach.
            if (!alignmentModel.getVersion().equals(project.getVersion())) {
                logger.warn("Another plugin has reset the version to {}. Resetting to {}",
                        project.getVersion(), alignmentModel.getVersion());
                project.setVersion(alignmentModel.getVersion());
            }

            // dependencyManagement is the extension that the Spring Dependency Management Plugin registers
            final Object obj = p.getExtensions().findByName("dependencyManagement");
            if (obj != null) {
                if (isDependencyManagementPluginPomCustomizationEnabled(obj)) {
                    throw new InvalidUserDataException(
                            "The ManipulationPlugin cannot be used together with the Spring Dependency Management unless the latter has disabled generatedPomCustomization");
                }
            }
        });

        // add actions to manipulate project
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule, resolvedDependenciesRepository));
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));

        configurePublishingTask(project, correspondingModule, resolvedDependenciesRepository);
    }

    // Ensure that if the Spring Dependency Management plugin is applied,
    // that it's configured to not generate a "dependencyManagement" section in the generated bom
    // This is needed because if we don't do it, the "dependencyManagement" section (which is a bom inclusion) will override our dependencies
    // On the implementation side of things, we need to use reflection because we can get a nasty classloader errors
    // when trying to cast the object to the known type
    private boolean isDependencyManagementPluginPomCustomizationEnabled(Object obj) {
        try {
            final Method getPomCustomizationSettingsMethod = obj.getClass().getMethod("getPomCustomizationSettings");
            final Object getPomCustomizationSettingsObj = getPomCustomizationSettingsMethod.invoke(obj);
            final Method isEnabledMethod = getPomCustomizationSettingsObj.getClass().getMethod("isEnabled");
            return (boolean) isEnabledMethod.invoke(getPomCustomizationSettingsObj);
        } catch (Exception e) {
            logger.error(
                    "ManipulationPlugin is being used with an unsupported version of the Spring Dependency Management Plugin",
                    e);
            throw new ManipulationUncheckedException(e);
        }
    }

    /**
     * TODO: add functional tests for publishing
     */
    private void configurePublishingTask(Project project, ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
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
                // make sure we first check for the maven publish plugin since it seems that when both are declared, this is the one used
                // see hibernate-enhance-maven-plugin
                if (evaluatedProject.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN)) {
                    deployPlugin = MAVEN_PUBLISH_PLUGIN;
                } else if (evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                    deployPlugin = LEGACY_MAVEN_PLUGIN;
                }
            }

            if (LEGACY_MAVEN_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring 'maven' plugin for project " + evaluatedProject.getName());
                evaluatedProject
                        .afterEvaluate(new UploadTaskTransformerAction(correspondingModule, resolvedDependenciesRepository));
                evaluatedProject.afterEvaluate(new MavenPublicationRepositoryAction());
            } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring 'maven-publish' plugin for project " + evaluatedProject.getName());

                evaluatedProject.afterEvaluate(new PublishingRepositoryAction());
                evaluatedProject
                        .afterEvaluate(new PublishingPomTransformerAction(correspondingModule, resolvedDependenciesRepository));
            } else {
                logger.warn("No publishing plugin was configured!");
            }
        });
    }
}
