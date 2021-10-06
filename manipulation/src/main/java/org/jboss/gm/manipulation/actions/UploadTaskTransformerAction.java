package org.jboss.gm.manipulation.actions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.manipulation.ResolvedDependenciesRepository;

import static org.jboss.gm.manipulation.ManipulationPlugin.LEGACY_MAVEN_PLUGIN;

/**
 * Fixes {@code pom.xml} generation in the old &quot;maven&quot; plugin.
 * <p>
 * Adds {@code PomTransformer} to all {@code MavenResolver} repositories in {@code Upload} tasks.
 * <p>
 * <em>
 * Gradle 7+ removed support for the old &quot;maven&quot; plugin. As such, this class uses reflection in order to build
 * and run on Gradle 7+.
 * </em>
 * </p>
 */
public class UploadTaskTransformerAction implements Action<Project> {

    private static final Logger LOGGER = GMLogger.getLogger(UploadTaskTransformerAction.class);
    private final ManipulationModel alignmentConfiguration;
    private final ResolvedDependenciesRepository resolvedDependenciesRepository;
    private Class<? extends ArtifactRepository> resolverClass;
    private Method getPomMethod;
    private Class<? extends Task> uploadClass;
    private Method getRepositoriesMethod;
    private Method withXmlMethod;

    /**
     * Creates a new upload task transformer action with the given alignment configuration and resolved dependencies
     * repository.
     *
     * @param alignmentConfiguration the alignment configuration
     * @param resolvedDependenciesRepository the resolved dependencies repository
     */
    public UploadTaskTransformerAction(ManipulationModel alignmentConfiguration,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.alignmentConfiguration = alignmentConfiguration;
        this.resolvedDependenciesRepository = resolvedDependenciesRepository;

        try {
            resolverClass = Class.forName("org.gradle.api.artifacts.maven.MavenResolver")
                    .asSubclass(ArtifactRepository.class);
            getPomMethod = resolverClass.getMethod("getPom");
            // XXX: This class is in Gradle 7, but deprecated
            // XXX: See <https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation>
            uploadClass = Class.forName("org.gradle.api.tasks.Upload").asSubclass(Task.class);
            getRepositoriesMethod = uploadClass.getMethod("getRepositories");
            Class<?> pomClass = Class.forName("org.gradle.api.artifacts.maven.MavenPom");
            withXmlMethod = pomClass.getDeclaredMethod("withXml", Action.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            LOGGER.error("Failed to find required class or method for project {}", alignmentConfiguration.getName(),
                    e);
        }
    }

    /**
     * Executes this upload task transformer action on the given project.
     *
     * @param project the project
     */
    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin(LEGACY_MAVEN_PLUGIN)) {
            return;
        }

        project.getTasks().withType(uploadClass).all((Task upload) -> {
            try {
                Object repositoryHandler = getRepositoriesMethod.invoke(upload);
                ((ArtifactRepositoryContainer) repositoryHandler).withType(resolverClass).all(this::execute);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.error("Execution error for project {}", project.getName(), e);
            }
        });
    }

    /**
     * Executes this upload task transformer action on the given artifact repository.
     *
     * @param repository the repository for resolving and publishing artifacts
     */
    private void execute(ArtifactRepository repository) {
        try {
            Object pom = getPomMethod.invoke(repository);
            Action<XmlProvider> action = new MavenPomTransformerAction(alignmentConfiguration,
                    resolvedDependenciesRepository);
            withXmlMethod.invoke(pom, action);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("Execution error for repository {}", repository.getName(), e);
        }
    }
}
