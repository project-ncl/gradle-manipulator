package org.jboss.gm.common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.impl.Version;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.PluginUtils;
import org.jboss.gm.common.utils.PluginUtils.DokkaVersion;
import org.jboss.gm.common.utils.ProjectUtils;
import org.jboss.gm.common.versioning.RelaxedProjectVersionRef;

/**
 * Cache that is stored in the root project extensions.
 */
public class ManipulationCache {

    private static final String NAME_PREFIX = "manipulationModelCache";

    /**
     * Root project.
     *
     * @return the root project
     */
    @Getter
    private final Project rootProject;

    /**
     * Will be built up to contain all the projects that need alignment. The same reference is passed to each task
     * and is used to make sure that the result of alignment is only written once (by the last alignment task to be
     * performed).
     */
    private final Set<String> projectCounter = Collections.synchronizedSet(new HashSet<>());

    /**
     * Root model.
     *
     * @return the root model
     */
    @Getter
    private ManipulationModel model;

    private final List<ProjectVersionRef> projectVersionRefs = new ArrayList<>();

    /**
     * This is the project dependencies - it represents a mapping of project module to a map of the original Dependency
     * (which might be dynamic) to the fully resolved GAV.
     *
     * @return the project dependencies
     */
    @Getter
    private final Map<Project, Map<RelaxedProjectVersionRef, ProjectVersionRef>> dependencies = new LinkedHashMap<>();

    @Getter
    private final Map<ArtifactRepository, Path> repositories = new HashMap<>();

    @Getter
    @Setter
    private DokkaVersion dokkaVersion = DokkaVersion.NONE;

    /**
     * Retrieves the cache given any project. It will access the root project, check if the
     * cache exists and create it if required.
     * <p>
     * <b>Only used by AlignmentPlugin</b>
     *
     * @param project the {@link Project} reference.
     * @param model the {@link ManipulationModel} to set in the cache.
     * @return the ManipulationCache object.
     */
    public static ManipulationCache getCache(Project project, ManipulationModel model) {
        ManipulationCache cache = getCache(project);
        cache.model = model;
        return cache;
    }

    /**
     * Retrieves the cache given any project. It will access the root project, check if the
     * cache exists and create it if required.
     *
     * @param project the {@link Project} reference.
     * @return the ManipulationCache object.
     */
    public static ManipulationCache getCache(Project project) {
        if (project == null) {
            throw new ManipulationUncheckedException("Null project");
        }
        ManipulationCache cache;

        if (!project.getRootProject().getExtensions().getExtraProperties().has(NAME_PREFIX)) {
            cache = new ManipulationCache(project.getRootProject());
            project.getRootProject().getExtensions().getExtraProperties().set(NAME_PREFIX, cache);
        } else {
            cache = (ManipulationCache) project.getRootProject().getExtensions().getExtraProperties().get(NAME_PREFIX);
        }
        return cache;
    }

    private ManipulationCache(Project rootProject) {
        this.rootProject = rootProject;
    }

    /**
     * Used to track the number of projects/subprojects.
     *
     * @param project the project
     */
    public void addProject(Project project) {
        projectCounter.add(project.getPath());
    }

    /**
     * Tracking projects - remove the project when it is evaluated.
     *
     * @param project the project
     * @return true if all projects are now handled.
     */
    public boolean removeProject(Project project) {
        projectCounter.remove(project.getPath());
        return projectCounter.isEmpty();
    }

    public void addDependencies(Project project, Map<RelaxedProjectVersionRef, ProjectVersionRef> deps) {
        dependencies.put(project, deps);
    }

    /**
     * As well as storing the GAV, at this point when the GAV is available the project group is now defined.
     * Therefore, update the model with the correct groupId.
     *
     * @param project the current Project instance
     * @param gav the GAV to store.
     */
    public void addGAV(Project project, ProjectVersionRef gav) {
        // Null check for some tests.
        if (project != null && model != null) {
            model.findCorrespondingChild(project).setGroup(ProjectUtils.getRealGroupId(project));
        }
        this.projectVersionRefs.add(gav);
    }

    @Override
    public String toString() {
        return rootProject.getName();
    }

    public void addRepository(ArtifactRepository repository, Path projectDir) {
        repositories.put(repository, projectDir);
    }

    public List<ProjectVersionRef> getProjectVersionRefs(boolean versionSuffixSnapshot) {
        return projectVersionRefs.stream()
                .map(e -> !versionSuffixSnapshot
                        ? new SimpleProjectVersionRef(e.asProjectRef(), Version.removeSnapshot(e.getVersionString()))
                        : e)
                .collect(Collectors.toList());
    }

    public String getProjectCounterRemaining() {
        return projectCounter.toString();
    }
}
