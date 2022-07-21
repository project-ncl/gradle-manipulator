package org.jboss.gm.manipulation.actions;

import java.util.concurrent.atomic.AtomicBoolean;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.GMLogger;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jboss.gm.manipulation.ManipulationPlugin.MAVEN_PUBLISH_PLUGIN;

/**
 * Adds a publishing repository specific to PNC environment.
 * <p>
 * System properties "AProxDeployUrl" and "accessToken" has to be defined during build.
 * <p>
 * Is equivalent to following gradle snippet:
 *
 * <pre>
 * publishing {
 *         repositories {
 *             maven {
 *                 name = "PNC"
 *                 url = System.getProperty('AProxDeployUrl')
 *                 credentials(HttpHeaderCredentials) {
 *                     name = "Authorization"
 *                     value = "Bearer " + System.getProperty('accessToken')
 *                 }
 *                 authentication {
 *                     header(HttpHeaderAuthentication)
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 */
public class MavenPublishingRepositoryAction implements Action<Project> {

    static final String REPO_NAME = "GME";

    private static final String BUILD_SRC = "buildSrc";

    private final Logger logger = GMLogger.getLogger(getClass());

    @Override
    public void execute(Project project) {
        AtomicBoolean publishBuildSrc = new AtomicBoolean(false);

        if (project.getProjectDir().getName().equals(BUILD_SRC) && !publishBuildSrc.get()) {
            // Only ignore buildSrc if it doesn't have an existing publishing mechanism.
            logger.warn(
                    "Not adding publishing extension to project {} as {} is build-time only and not configured for publishing.",
                    project, BUILD_SRC);
            return;
        }

        if (!project.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN)) {
            // This should never happen due to prior checks in ManipulationPlugin
            throw new ManipulationUncheckedException(
                    "Cannot configure publishing repository, maven-publish plugin was not detected.");
        }

        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        if (isEmpty(config.deployUrl())) {
            logger.warn("Publishing URL was not configured.");
            return;
        }

        if (isEmpty(config.accessToken())) {
            logger.warn("No authentication token was configured.");
        }

        project.getTasks().configureEach(task -> {
            // disable existing publishing tasks but make sure we keep ours
            if (task.getName().startsWith("publish")
                    && task.getName().endsWith("Repository")
                    && !task.getName().contains(REPO_NAME)) {
                logger.info("Disabling publishing task {} for project {}", task.getName(), project.getName());
                task.setEnabled(false);
                publishBuildSrc.set(true);
            }
        });
        RepositoryHandler repos = project.getExtensions().getByType(PublishingExtension.class).getRepositories();
        repos.forEach(repository -> {
            if (!REPO_NAME.equals(repository.getName())) {
                logger.info("Removing publishing repository {}", repository.getName());
            }
        });
        repos.removeIf(artifactRepository -> !artifactRepository.getName().equals(REPO_NAME));

        project.getExtensions().getByType(PublishingExtension.class).getRepositories().maven(repository -> {
            // To avoid names like "publishPluginMavenPublicationToManipulator Publishing RepositoryRepository"
            // rather than naming this "Manipulator Publishing Repository" use GME for relative uniqueness.
            repository.setName(REPO_NAME);
            repository.setUrl(config.deployUrl());
            if (!isEmpty(config.accessToken())) {
                //noinspection UnstableApiUsage
                repository.credentials(HttpHeaderCredentials.class, cred -> {
                    cred.setName("Authorization");
                    cred.setValue("Bearer " + config.accessToken());
                });
                //noinspection UnstableApiUsage
                repository.getAuthentication().register("header", HttpHeaderAuthentication.class);
            }
        });
    }
}
