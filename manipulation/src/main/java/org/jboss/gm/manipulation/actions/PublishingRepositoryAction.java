package org.jboss.gm.manipulation.actions;

import static org.apache.commons.lang.StringUtils.isEmpty;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.jboss.gm.common.Configuration;

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
public class PublishingRepositoryAction implements Action<Project> {

    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin("maven-publish")) {
            project.getLogger().warn("Cannot configure publishing repository, maven-publish plugin was not detected.");
            return;
        }

        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        if (isEmpty(config.deployUrl())) {
            project.getLogger().warn("Publishing URL was not configured.");
            return;
        }

        if (isEmpty(config.accessToken())) {
            project.getLogger().warn("No authentication token was configured.");
        }

        project.getPlugins().withType(MavenPublishPlugin.class, plugin -> {
            project.getExtensions().configure(PublishingExtension.class, publishingExtension -> {
                publishingExtension.getRepositories().maven(repository -> {
                    repository.setName("Manipulator Publishing Repository");
                    repository.setUrl(config.deployUrl());
                    if (!isEmpty(config.deployUrl())) {
                        repository.credentials(HttpHeaderCredentials.class, cred -> {
                            cred.setName("Authorization");
                            cred.setValue("Bearer " + config.accessToken());
                        });
                        repository.getAuthentication().create("header", HttpHeaderAuthentication.class);
                    }
                });
            });
        });
    }
}
