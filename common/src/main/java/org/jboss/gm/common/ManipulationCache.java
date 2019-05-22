package org.jboss.gm.common;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Cache that is stored in the root project extensions.
 */
public class ManipulationCache {

    private static final String NAME_PREFIX = "manipulationModelCache";

    /** Root project */
    private Project project;

    /**
     * Will be built up to contain all the projects that need alignment. The same reference is passed to each task
     * and is used to make sure that the result of alignment is only written once (by the last alignment task to be performed)
     */
    private HashSet<String> projectCounter = new HashSet<>();

    private ManipulationModel rootModel;

    private ArrayDeque<ProjectVersionRef> projectVersionRefs = new ArrayDeque<>();

    private Map<Project, Collection<ProjectVersionRef>> projectDependencies = new HashMap<>();

    /**
     * Retrieves the cache given any project. It will access the root project, check if the
     * cache exists and create it if required.
     * <p>
     * </p>
     * <b>Only used by AlignmentPlugin</b>
     *
     * @param project the {@link Project} reference.
     * @param model the {@link ManipulationModel} to set in the cache.
     * @return the ManipulationCache object.
     */
    public static ManipulationCache getCache(Project project, ManipulationModel model) {
        ManipulationCache cache = getCache(project);
        cache.rootModel = model;
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
        this.project = rootProject;
    }

    /**
     * Used to track the number of projects/subprojects.
     *
     * @param name the project name
     */
    public void addProject(String name) {
        projectCounter.add(name);
    }

    /**
     * Tracking projects - remove the named project when it is evaluated.
     *
     * @param name the name of the project
     * @return true if all projects are now handled.
     */
    public boolean removeProject(String name) {
        projectCounter.remove(name);
        return projectCounter.isEmpty();
    }

    public ManipulationModel getModel() {
        return rootModel;
    }

    public void addDependencies(Project project, Collection<ProjectVersionRef> deps) {
        projectDependencies.put(project, deps);
    }

    public void addGAV(ProjectVersionRef gav) {
        projectVersionRefs.add(gav);
    }

    public ArrayDeque<ProjectVersionRef> getGAV() {
        return projectVersionRefs;
    }

    public Map<Project, Collection<ProjectVersionRef>> getDependencies() {
        return projectDependencies;
    }

    @Override
    public String toString() {
        return project.getName();
    }
}
