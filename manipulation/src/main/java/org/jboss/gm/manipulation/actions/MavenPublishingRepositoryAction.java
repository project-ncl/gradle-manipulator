package org.jboss.gm.manipulation.actions;

import org.aeonbits.owner.ConfigCache;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.GMLogger;

import static org.apache.commons.lang.StringUtils.isEmpty;

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

    private final Logger logger = GMLogger.getLogger(getClass());

    @Override
    public void execute(Project project) {

        // disable existing publishing tasks but make sure we keep ours
        project.afterEvaluate(p -> p.getTasks().stream()
                .filter(t -> t.getName().startsWith("publish") && t.getName().endsWith("Repository")
                        && !t.getName().contains(REPO_NAME))
                .forEach(t -> {
                    logger.info("Disabling publishing task " + t.getName());
                    t.setEnabled(false);
                }));

        if (project.getProjectDir().getName().equals("buildSrc")) {
            logger.warn("Not adding publishing extension to project {} as buildSrc is build-time only.", project);
            return;
        } else if (!project.getPluginManager().hasPlugin("maven-publish")) {
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
                repository.getAuthentication().create("header", HttpHeaderAuthentication.class);
            }
        });
    }
}
