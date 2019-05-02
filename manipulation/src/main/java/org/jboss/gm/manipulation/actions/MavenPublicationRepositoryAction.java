package org.jboss.gm.manipulation.actions;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.credentials.HttpHeaderCredentials;
import org.gradle.api.tasks.Upload;
import org.gradle.authentication.http.HttpHeaderAuthentication;
import org.jboss.gm.common.Configuration;

/**
 * Adds a publication repository to the legacy maven plugin.
 *
 * Repository URL and authentication token need to be configured externally.
 */
public class MavenPublicationRepositoryAction implements Action<Project> {

    @Override
    public void execute(Project project) {
        Configuration config = ConfigCache.getOrCreate(Configuration.class);

        Upload uploadArchives = project.getTasks().withType(Upload.class).findByName("uploadArchives");
        if (uploadArchives == null) {
            project.getLogger().warn("'uploadArchives' task not found, publication repository will not be configured.");
            return;
        }

        if (isEmpty(config.deployUrl())) {
            project.getLogger().warn("Publishing URL was not configured.");
            return;
        }

        if (isEmpty(config.accessToken())) {
            project.getLogger().warn("No authentication token was configured.");
        }

        uploadArchives.getRepositories().maven(mavenArtifactRepository -> {
            mavenArtifactRepository.setName("Manipulator Publishing Repository");
            mavenArtifactRepository.setUrl(config.deployUrl());
            if (config.accessToken() != null) {
                mavenArtifactRepository.credentials(HttpHeaderCredentials.class, cred -> {
                    cred.setName("Authorization");
                    cred.setValue("Bearer " + config.accessToken());
                });
                mavenArtifactRepository.getAuthentication().create("header", HttpHeaderAuthentication.class);
            }
        });
    }
}
