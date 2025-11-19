package org.jboss.pnc.gradlemanipulator.analyzer.alignment.io;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository;
import org.gradle.api.logging.Logger;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.jboss.pnc.mavenmanipulator.io.SettingsIO;

/**
 * Exports artifact repositories in a maven settings format.
 *
 * @author Tomas Hofman
 */
public final class RepositoryExporter {

    private static final Logger logger = GMLogger.getLogger(RepositoryExporter.class);

    // only export remote URLs
    private static final Collection<String> SUPPORTED_SCHEMES = Arrays.asList("http", "https");

    private final Settings mavenSettings = new Settings();
    private final Profile mavenProfile = new Profile();
    private final Set<String> exportedUrls = new HashSet<>();
    private int repositoryCounter = 0;

    private enum REPO_TYPE {
        Maven,
        Ivy
    }

    private RepositoryExporter() {
        mavenSettings.getProfiles().add(mavenProfile);
        mavenProfile.setId("generated-by-gme");
        mavenSettings.addActiveProfile(mavenProfile.getId());
    }

    /**
     * Exports all repositories to the given settings file.
     *
     * @param repositories the repositories to export
     * @param settingsFile the settings file to write to
     */
    public static void export(Map<ArtifactRepository, Path> repositories, File settingsFile) {
        RepositoryExporter repositoryExporter = new RepositoryExporter();
        processRepositories(repositoryExporter, repositories);

        SettingsIO settingsWriter = new SettingsIO(new DefaultSettingsBuilderFactory().newInstance());
        logger.debug("Writing repository settings into {}", settingsFile.getAbsolutePath());
        try {
            settingsWriter.write(repositoryExporter.mavenSettings, settingsFile);
        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException(
                    "Could not write repository settings file into {}",
                    settingsFile.getAbsolutePath());
        }
    }

    private static void processRepositories(
            RepositoryExporter repositoryExporter,
            Map<ArtifactRepository, Path> repositories) {
        for (Map.Entry<ArtifactRepository, Path> entry : repositories.entrySet()) {
            ArtifactRepository repository = entry.getKey();
            Path path = entry.getValue();
            if (repository instanceof DefaultMavenLocalArtifactRepository) {
                DefaultMavenLocalArtifactRepository artifactRepository = (DefaultMavenLocalArtifactRepository) repository;
                logger.debug("Skipping local maven repository '{}' from {}", artifactRepository.getUrl(), path);
            } else if (repository instanceof MavenArtifactRepository) {
                MavenArtifactRepository artifactRepository = (MavenArtifactRepository) repository;
                URI url = artifactRepository.getUrl();

                if (isSupportedScheme(url)) {
                    addRepository(REPO_TYPE.Maven, repositoryExporter, artifactRepository.getName(), url.toString());
                } else {
                    logger.debug(
                            "Skipping maven repository '{}' with unsupported scheme {} from {}",
                            repository.getName(),
                            url,
                            path);
                }
            } else if (repository instanceof IvyArtifactRepository) {
                IvyArtifactRepository artifactRepository = (IvyArtifactRepository) repository;
                URI url = artifactRepository.getUrl();

                if (isSupportedScheme(url)) {
                    addRepository(REPO_TYPE.Ivy, repositoryExporter, artifactRepository.getName(), url.toString());
                } else {
                    logger.debug(
                            "Skipping ivy repository '{}' with unsupported scheme {} from {}",
                            repository.getName(),
                            url,
                            path);
                }
            } else {
                logger.debug(
                        "Skipping repository of type {} from {}",
                        repository.getClass().getSimpleName(),
                        path);
            }
        }
    }

    private static void addRepository(REPO_TYPE type, RepositoryExporter repositoryExporter, String name, String url) {
        if (repositoryExporter.exportedUrls.contains(url)) {
            // skip URLs we've already seen
            return;
        }

        logger.debug("Adding {} repository: {}", type, url);

        String repoId = name + "-" + repositoryExporter.repositoryCounter++; // counter ensures that the id is unique
        Repository mavenRepository = new Repository();
        mavenRepository.setId(repoId);
        mavenRepository.setName(repoId);
        mavenRepository.setUrl(url);
        mavenRepository.setSnapshots(new RepositoryPolicy());
        mavenRepository.setReleases(new RepositoryPolicy());

        repositoryExporter.exportedUrls.add(url);
        repositoryExporter.mavenProfile.addRepository(mavenRepository);
    }

    private static boolean isSupportedScheme(URI url) {
        return url != null && url.getScheme() != null && SUPPORTED_SCHEMES.contains(url.getScheme().toLowerCase());
    }
}
