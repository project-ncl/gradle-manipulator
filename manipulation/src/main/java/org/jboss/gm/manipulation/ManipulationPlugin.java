package org.jboss.gm.manipulation;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.MavenPlugin;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.ManifestUtils;
import org.jboss.gm.manipulation.actions.LegacyMavenPublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.MavenPomTransformerAction;
import org.jboss.gm.manipulation.actions.MavenPublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.PublishingArtifactsAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;

@SuppressWarnings("unused")
public class ManipulationPlugin implements Plugin<Project> {

    private static final String LEGACY_MAVEN_PLUGIN = "maven";
    private static final String MAVEN_PUBLISH_PLUGIN = "maven-publish";

    static {
        System.out.println("Running Gradle Manipulation Plugin " + ManifestUtils.getManifestInformation());
    }

    private final Logger logger = GMLogger.getLogger(getClass());

    private static final AtomicBoolean configOutput = new AtomicBoolean();

    @Override
    public void apply(Project project) {

        if (!new File(project.getRootDir(), ManipulationIO.MANIPULATION_FILE_NAME).exists()) {
            logger.error("No {} found in {}; exiting plugin.", ManipulationIO.MANIPULATION_FILE_NAME, project.getRootDir());
            return;
        }
        // We can't ignore projects like buildSrc (which are purely build-time) here as we still might need to process them to
        // e.g. remove any publishing tasks.

        if (System.getProperty("gmeFunctionalTest") != null) {
            ConfigCache.getOrCreate(Configuration.class).reload();
        }

        Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
        if (configOutput.compareAndSet(false, true)) {
            // Only output the config once to avoid noisy logging.
            configuration.dumpCurrentConfig(logger);
        }

        // get the previously performed alignment
        final ManipulationModel alignmentModel = ManipulationIO.readManipulationModel(project.getRootDir());
        final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project);

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

        configurePublishingTask(configuration, project, correspondingModule, resolvedDependenciesRepository);
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
    private void configurePublishingTask(Configuration config, Project project, ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        project.afterEvaluate(evaluatedProject -> {
            // we need to determine which plugin to configure for publication

            // first, let the choice be enforced via a system property
            String deployPlugin = config.deployPlugin();
            if (!isEmpty(deployPlugin)) {
                logger.info("Enforcing artifact deployment plugin `{}`.", deployPlugin);

                checkEnforcedPluginSetting(evaluatedProject, deployPlugin);

                // if enforced plugin is not configured in the project, apply it
                if (LEGACY_MAVEN_PLUGIN.equals(deployPlugin)) {
                    if (!evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                        evaluatedProject.getPluginManager().apply(MavenPlugin.class);
                    }
                } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)) {
                    if (!evaluatedProject.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN)) {
                        evaluatedProject.getPluginManager().apply(MavenPublishPlugin.class);
                        evaluatedProject.afterEvaluate(new PublishingArtifactsAction());
                    }
                } else {
                    throw new InvalidUserDataException("Unknown publishing plugin preference: " + deployPlugin);
                }
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
                evaluatedProject.afterEvaluate(new LegacyMavenPublishingRepositoryAction());
            } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring 'maven-publish' plugin for project " + evaluatedProject.getName());

                evaluatedProject.afterEvaluate(new MavenPublishingRepositoryAction());
                evaluatedProject
                        .afterEvaluate(new MavenPomTransformerAction(correspondingModule, resolvedDependenciesRepository));
            } else {
                logger.warn("No publishing plugin was configured!");
            }
        });
    }

    private static void checkEnforcedPluginSetting(Project evaluatedProject, String enforcedPlugin) {
        String otherPlugin = LEGACY_MAVEN_PLUGIN.equals(enforcedPlugin) ? MAVEN_PUBLISH_PLUGIN : LEGACY_MAVEN_PLUGIN;
        if (evaluatedProject.getPluginManager().hasPlugin(otherPlugin)) {
            throw new InvalidUserDataException("User configuration enforces " + enforcedPlugin
                    + " but project already uses " + otherPlugin);
        }
    }
}
