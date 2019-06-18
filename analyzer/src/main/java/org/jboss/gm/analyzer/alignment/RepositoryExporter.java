package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.io.SettingsIO;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports artifact repositories in a maven settings format.
 *
 * @author Tomas Hofman
 */
public class RepositoryExporter {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryExporter.class);

    // only export remote URLs
    private static final Collection<String> SUPPORTED_SCHEMES = Arrays.asList("http", "https");

    private Settings mavenSettings = new Settings();
    private Profile mavenProfile = new Profile();
    private int repositoryCounter = 0;
    private Set<String> exportedUrls = new HashSet<>();

    public RepositoryExporter(Collection<ArtifactRepository> repositories) {
        mavenSettings.getProfiles().add(mavenProfile);
        mavenProfile.setId("generated-by-gme");
        mavenSettings.addActiveProfile(mavenProfile.getId());

        addDefaultRepositories();
        processRepositories(repositories);
    }

    public void export(File settingsFile) {
        SettingsIO settingsWriter = new SettingsIO(new DefaultSettingsBuilder());
        logger.debug("Writing repository settings into {}", settingsFile.getAbsolutePath());
        try {
            settingsWriter.write(mavenSettings, settingsFile);
        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException("Could not write repository settings file into "
                    + settingsFile.getAbsolutePath());
        }
    }

    private void processRepositories(Collection<ArtifactRepository> repositories) {
        for (ArtifactRepository repository : repositories) {
            if (repository instanceof DefaultMavenLocalArtifactRepository) {
                DefaultMavenLocalArtifactRepository artifactRepository = (DefaultMavenLocalArtifactRepository) repository;
                logger.debug("Skipping local maven repository: {}", artifactRepository.getUrl());
            } else if (repository instanceof MavenArtifactRepository) {
                MavenArtifactRepository artifactRepository = (MavenArtifactRepository) repository;
                URI url = artifactRepository.getUrl();
                if (isSuportedScheme(url)) {
                    logger.debug("Adding maven repository: {}", url);
                    addRepository(artifactRepository.getName(), url.toString());
                } else {
                    logger.debug("Skipping maven repository with unsupported scheme: {}", url);
                }
            } else if (repository instanceof IvyArtifactRepository) {
                IvyArtifactRepository artifactRepository = (IvyArtifactRepository) repository;
                URI url = artifactRepository.getUrl();
                if (isSuportedScheme(url)) {
                    logger.debug("Adding ivy repository: {}", url);
                    addRepository(artifactRepository.getName(), url.toString());
                } else {
                    logger.debug("Skipping ivy repository with unsupported scheme: {}", url);
                }
            } else {
                logger.debug("Skipping repository of type {}", repository.getClass().getSimpleName());
            }
        }
    }

    private void addRepository(String name, String url) {
        if (exportedUrls.contains(url)) {
            // skip URLs we've already seen
            return;
        }
        exportedUrls.add(url);

        String repoId = name + "-" + repositoryCounter++; // counter ensures that the id is unique
        Repository mavenRepository = new Repository();
        mavenRepository.setId(repoId);
        mavenRepository.setName(repoId);
        mavenRepository.setUrl(url);
        mavenRepository.setSnapshots(new RepositoryPolicy());
        mavenRepository.setReleases(new RepositoryPolicy());
        mavenProfile.addRepository(mavenRepository);
    }

    private static boolean isSuportedScheme(URI url) {
        return url.getScheme() != null && SUPPORTED_SCHEMES.contains(url.getScheme().toLowerCase());
    }

    private void addDefaultRepositories() {
        addRepository("Gradle Plugin Repository", "https://plugins.gradle.org/m2/");
    }
}
