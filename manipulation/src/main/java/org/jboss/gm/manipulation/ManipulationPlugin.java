package org.jboss.gm.manipulation;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.beanutils.ContextClassLoaderLocal;
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
import org.jboss.gm.common.utils.ProjectUtils;
import org.jboss.gm.manipulation.actions.LegacyMavenPublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.MavenPomTransformerAction;
import org.jboss.gm.manipulation.actions.MavenPublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.PublishingArtifactsAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class ManipulationPlugin implements Plugin<Project> {

    public static final String LEGACY_MAVEN_PLUGIN = "maven";
    // This plugin wraps the legacy maven plugin.
    private static final String LEGACY_MAVEN_PLUGIN_NEXUS = "com.bmuschko.nexus";
    private static final String MAVEN_PUBLISH_PLUGIN = "maven-publish";

    static {
        System.out.println("Running Gradle Manipulation Plugin " + ManifestUtils.getManifestInformation());
    }

    private final Logger logger = GMLogger.getLogger(getClass());

    private static final ContextClassLoaderLocal<AtomicBoolean> configOutput = new ContextClassLoaderLocal<AtomicBoolean>() {
        @Override
        protected AtomicBoolean initialValue() {
            return new AtomicBoolean();
        }
    };

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
        if (!configOutput.get().getAndSet(true)) {
            // Only output the config once to avoid noisy logging.
            logger.info("Configuration now has properties {}", configuration.dumpCurrentConfig());
        }

        // get the previously performed alignment
        final ManipulationModel alignmentModel = ManipulationIO.readManipulationModel(project.getRootDir());
        final ManipulationModel correspondingModule = alignmentModel.findCorrespondingChild(project);

        // we need to change the project version early so various tasks that ready early and create other vars based on it
        // (like the zip tasks) can use the correct version
        logger.info("Updating project version {} to {} ", project.getVersion(), alignmentModel.getVersion());
        project.setVersion(alignmentModel.getVersion());

        final ResolvedDependenciesRepository resolvedDependenciesRepository = new ResolvedDependenciesRepository();

        project.afterEvaluate(ignore -> {
            final Object abn = project.findProperty("archivesBaseName");
            final String originalName = project.getName();

            // This double version set is required - sometimes other plugins seem to override the version we set initially.
            // We need to set it at the start as other plugins also require it there. Hence this belt and braces approach.
            if (!alignmentModel.getVersion().equals(project.getVersion())) {
                logger.warn("Another plugin has reset the version to {}. Resetting to {}",
                        project.getVersion(), alignmentModel.getVersion());
                project.setVersion(alignmentModel.getVersion());
            }
            if (abn != null && !originalName.equals(abn)) {
                logger.warn("Located archivesBaseName override ; forcing project name to '{}' from '{}' for correct usage",
                        abn, originalName);
                ProjectUtils.updateNameField(project, abn);
            }
        });

        // add actions to manipulate project
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule, resolvedDependenciesRepository));
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));

        configurePublishingTask(configuration, project, correspondingModule, resolvedDependenciesRepository);
    }

    private void configurePublishingTask(Configuration config, Project project, ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        project.afterEvaluate(evaluatedProject -> {
            // we need to determine which plugin to configure for publication

            // first, let the choice be enforced via a system property
            String deployPlugin = config.deployPlugin();
            if (isNotEmpty(deployPlugin)) {
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
                } else if (evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN_NEXUS)) {
                    deployPlugin = LEGACY_MAVEN_PLUGIN_NEXUS;
                } else if (evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                    deployPlugin = LEGACY_MAVEN_PLUGIN;
                }
                if (evaluatedProject.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN) &&
                        evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                    logger.warn("Both deprecated maven plugin and current maven-publish plugin declared in project");
                }
            }

            if (LEGACY_MAVEN_PLUGIN.equals(deployPlugin) || LEGACY_MAVEN_PLUGIN_NEXUS.equals(deployPlugin)) {
                logger.info("Configuring {} plugin for project {}", deployPlugin, evaluatedProject.getName());
                evaluatedProject
                        .afterEvaluate(new UploadTaskTransformerAction(correspondingModule, resolvedDependenciesRepository));
                evaluatedProject.afterEvaluate(new LegacyMavenPublishingRepositoryAction());

                if (project.getGradle().getStartParameter().getTaskNames().stream()
                        .noneMatch(p -> p.contains("uploadArchives"))) {
                    logger.error(
                            "Unable to find uploadArchives parameter for Legacy Maven Plugin.");
                }
            } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring {} plugin for project {}", deployPlugin, evaluatedProject.getName());

                evaluatedProject.afterEvaluate(new MavenPublishingRepositoryAction());
                evaluatedProject
                        .afterEvaluate(new MavenPomTransformerAction(correspondingModule, resolvedDependenciesRepository));

                if (project.getGradle().getStartParameter().getTaskNames().stream().noneMatch(p -> p.contains("publish"))) {
                    logger.error(
                            "Unable to find publish parameter for Maven Publish Plugin.");
                }
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
