/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 * <p>
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.gm.manipulation.actions;

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
            return;
        }

        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        String pncDeployUrl = config.deployUrl();
        if (pncDeployUrl != null) {
            project.getPlugins().withType(MavenPublishPlugin.class, plugin -> {
                project.getExtensions().configure(PublishingExtension.class, publishingExtension -> {
                    publishingExtension.getRepositories().maven(repository -> {
                        repository.setName("PNC");
                        repository.setUrl(pncDeployUrl);
                        repository.credentials(HttpHeaderCredentials.class, cred -> {
                            cred.setName("Authorization");
                            cred.setValue("Bearer " + config.accessToken());
                        });
                        repository.getAuthentication().create("header", HttpHeaderAuthentication.class);
                    });
                });
            });
        }
    }
}
