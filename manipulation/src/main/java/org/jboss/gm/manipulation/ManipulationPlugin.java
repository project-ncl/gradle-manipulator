package org.jboss.gm.manipulation;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aeonbits.owner.ConfigCache;
import org.apache.commons.beanutils.ContextClassLoaderLocal;
import org.commonjava.maven.ext.common.util.ManifestUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.plugins.signing.SigningExtension;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.io.ManipulationIO;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.ProjectUtils;
import org.jboss.gm.manipulation.actions.LegacyMavenPublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.ManifestUpdateAction;
import org.jboss.gm.manipulation.actions.MavenPublishingRepositoryAction;
import org.jboss.gm.manipulation.actions.OverrideDependenciesAction;
import org.jboss.gm.manipulation.actions.PublishTaskTransformerAction;
import org.jboss.gm.manipulation.actions.PublishingArtifactsAction;
import org.jboss.gm.manipulation.actions.UploadTaskTransformerAction;

/**
 * The manipulation plugin.
 */
public class ManipulationPlugin implements Plugin<Project> {
    /**
     * The name of the legacy maven plugin, i.e., &quot;maven&quot;.
     */
    public static final String LEGACY_MAVEN_PLUGIN = "maven";
    // This plugin wraps the legacy maven plugin.
    /**
     * The name of the legacy nexus maven plugin, i.e., &quot;com.bmuschko.nexus&quot;.
     */
    private static final String LEGACY_MAVEN_PLUGIN_NEXUS = "com.bmuschko.nexus";
    // Plugin "nebula.maven-base-publish" encompasses maven-publish
    /**
     * The name of the maven publish plugin, i.e., &quot;maven-publish&quot;.
     */
    public static final String MAVEN_PUBLISH_PLUGIN = "maven-publish";

    static {
        System.out.println(
                "Running Gradle Manipulation Plugin " + ManifestUtils.getManifestInformation(ManipulationPlugin.class));
    }

    private final Logger logger = GMLogger.getLogger(getClass());

    private static final ContextClassLoaderLocal<AtomicBoolean> configOutput = new ContextClassLoaderLocal<AtomicBoolean>() {
        @Override
        protected AtomicBoolean initialValue() {
            return new AtomicBoolean();
        }
    };

    /**
     * Applies the manipulation plugin to the given project.
     *
     * @param project the project
     */
    @Override
    public void apply(Project project) {
        if (System.getProperty("gmeFunctionalTest") != null) {
            ConfigCache.getOrCreate(Configuration.class).reload();
        }

        Configuration configuration = ConfigCache.getOrCreate(Configuration.class);
        if (!configOutput.get().getAndSet(true)) {
            // Only output the config once to avoid noisy logging.
            logger.info("Configuration now has properties {}", configuration.dumpCurrentConfig());
        }
        if (configuration.disableGME()) {
            logger.info("Gradle Manipulator disabled");
            return;
        }

        if (!new File(project.getRootDir(), ManipulationIO.MANIPULATION_FILE_NAME).exists()) {
            logger.error(
                    "No {} found in {}; exiting plugin.",
                    ManipulationIO.MANIPULATION_FILE_NAME,
                    project.getRootDir());
            return;
        }

        // get the previously performed alignment
        final ManipulationModel correspondingModule = ManipulationIO.readManipulationModel(project.getRootDir())
                .findCorrespondingChild(project);

        if (!project.getVersion().equals(correspondingModule.getVersion())) {
            // we need to change the project version early so various tasks that ready early and create other vars based on it
            // (like the zip tasks) can use the correct version
            logger.info(
                    "Updating project ({}) version {} to {}",
                    project.getProjectDir(),
                    project.getVersion(),
                    correspondingModule.getVersion());
            project.setVersion(correspondingModule.getVersion());

            project.afterEvaluate(ignore -> {
                // This double version set is required - sometimes other plugins seem to override the version we set initially.
                // We need to set it at the start as other plugins also require it there. Hence this belt and braces approach.
                if (!correspondingModule.getVersion().equals(project.getVersion())) {
                    logger.warn(
                            "After evaluation, another plugin has reset the version to {}. Resetting to {}",
                            project.getVersion(),
                            correspondingModule.getVersion());
                    project.setVersion(correspondingModule.getVersion());
                }
            });
        } else {
            logger.info(
                    "Not updating project ({}) since version ({}) has not changed",
                    project.getProjectDir(),
                    project.getVersion());
        }

        final ResolvedDependenciesRepository resolvedDependenciesRepository = new ResolvedDependenciesRepository();

        // add actions to manipulate project
        project.afterEvaluate(new OverrideDependenciesAction(correspondingModule, resolvedDependenciesRepository));
        project.afterEvaluate(new ManifestUpdateAction(correspondingModule));
        project.afterEvaluate(p -> p.getConfigurations().configureEach(c -> {
            if (c.isCanBeResolved()) {
                ProjectUtils.updateResolutionStrategy(c);
            }
        }));

        configurePublishingTask(configuration, project, correspondingModule, resolvedDependenciesRepository, "");

        for (String hook : configuration.publishPluginHooks()) {
            project.getPluginManager().withPlugin(hook, action -> {
                configurePublishingTask(
                        configuration,
                        project,
                        correspondingModule,
                        resolvedDependenciesRepository,
                        hook);
            });
        }
    }

    private void configurePublishingTask(
            Configuration config,
            Project project,
            ManipulationModel correspondingModule,
            ResolvedDependenciesRepository resolvedDependenciesRepository,
            String pluginHook) {
        project.afterEvaluate(evaluatedProject -> {
            if (!isEmpty(pluginHook)) {
                logger.warn("Detected application of plugin hook {} and now running publishing task again", pluginHook);
            }
            // we need to determine which plugin to configure for publication
            // first, let the choice be enforced via a system property
            String deployPlugin = config.deployPlugin();
            if (isNotEmpty(deployPlugin)) {
                logger.info("Enforcing artifact deployment plugin `{}`.", deployPlugin);

                checkEnforcedPluginSetting(evaluatedProject, deployPlugin);

                // if enforced plugin is not configured in the project, apply it
                if (LEGACY_MAVEN_PLUGIN.equals(deployPlugin)) {
                    if (!evaluatedProject.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
                        try {
                            Class<?> mavenPluginClass = Class.forName("org.gradle.api.plugins.MavenPlugin");
                            evaluatedProject.getPluginManager().apply(mavenPluginClass);
                        } catch (ClassNotFoundException e) {
                            logger.error(
                                    "For project {}, found {} plugin, but the class org.gradle.api.plugins.MavenPlugin is not available",
                                    evaluatedProject.getName(),
                                    LEGACY_MAVEN_PLUGIN,
                                    e);
                        }
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

                final String archivesBaseName = ProjectUtils.getArchivesBaseName(evaluatedProject);
                if (archivesBaseName != null) {
                    logger.warn(
                            "Located archivesBaseName override ; forcing project name to '{}' from '{}' for correct usage",
                            archivesBaseName,
                            evaluatedProject.getName());
                    ProjectUtils.updateNameField(evaluatedProject, archivesBaseName);
                }

                evaluatedProject
                        .afterEvaluate(
                                new UploadTaskTransformerAction(correspondingModule, resolvedDependenciesRepository));
                evaluatedProject.afterEvaluate(new LegacyMavenPublishingRepositoryAction());

                List<String> taskNames = evaluatedProject.getGradle().getStartParameter().getTaskNames();

                if (taskNames.stream().noneMatch(p -> p.contains("uploadArchives"))) {
                    logger.error(
                            "Unable to find uploadArchives parameter in tasks {} for Legacy Maven Plugin for project {}",
                            taskNames,
                            evaluatedProject.getName());
                }
            } else if (MAVEN_PUBLISH_PLUGIN.equals(deployPlugin)) {
                logger.info("Configuring {} plugin for project {}", deployPlugin, evaluatedProject.getName());

                evaluatedProject.afterEvaluate(new MavenPublishingRepositoryAction());
                evaluatedProject
                        .afterEvaluate(
                                new PublishTaskTransformerAction(correspondingModule, resolvedDependenciesRepository));

                List<String> taskNames = evaluatedProject.getGradle().getStartParameter().getTaskNames();

                if (taskNames.stream().noneMatch(p -> p.contains("publish"))) {
                    logger.error(
                            "Unable to find publish parameter in tasks {} for Maven Publish Plugin for project {}",
                            taskNames,
                            evaluatedProject.getName());
                }
            } else {
                logger.warn("No publishing plugin was configured for '{}'!", evaluatedProject.getName());
            }
        });
    }

    private static void checkEnforcedPluginSetting(Project evaluatedProject, String enforcedPlugin) {
        String otherPlugin = LEGACY_MAVEN_PLUGIN.equals(enforcedPlugin) ? MAVEN_PUBLISH_PLUGIN : LEGACY_MAVEN_PLUGIN;
        if (evaluatedProject.getPluginManager().hasPlugin(otherPlugin)) {
            throw new InvalidUserDataException(
                    "User configuration enforces " + enforcedPlugin
                            + " but project already uses " + otherPlugin);
        }
    }

    // Move the call to disable signing plugin into the publishing task actions to avoid issues
    // when a publishing plugin that isn't natively configured but a later injection adds publishing
    // and triggers the signing injection.
    public static void disableSigning(Logger logger, Project project) {
        SigningExtension sign = project.getExtensions().findByType(SigningExtension.class);
        if (sign != null) {
            // While we can use "project.getGradle().getTaskGraph().whenReady" to avoid
            // "task graph not ready" issues the required block in the project may be evaluated before
            // GME then overrides the version so lines like
            // required { !version.endsWith("SNAPSHOT") && gradle.taskGraph.hasTask("uploadArchives") }
            // then don't work. Effectively this means we can't use isRequired or task graph so it is
            // easier to just blindly override the signing plugin and set it to not required all the time.
            logger.info("Found signing plugin; disabling");
            sign.setRequired(false);
        }
    }
}
