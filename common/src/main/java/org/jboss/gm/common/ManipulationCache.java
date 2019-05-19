package org.jboss.gm.common;

import java.util.HashSet;
import java.util.Set;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Cache that is stored in the root project extensions.
 */
// TODO: Consider use of lombok.
public class ManipulationCache {

    private static final String NAME_PREFIX = "manipulationModelCache";

    private Project project;

    /**
     * Will be built up to contain all the projects that need alignment. The same reference is passed to each task
     * and is used to make sure that the result of alignment is only written once (by the last alignment task to be performed)
     */
    private HashSet<String> allProjects = new HashSet<>();

    private ManipulationModel rootModel;

    /**
     * Retrieves the cache given any project. It will access the root project, check if the
     * cache exists and create it if required.
     * @param project the project reference.
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

    public void addProject(String name) {
        allProjects.add(name);
    }

    public Set<String> getProjects() {
        return allProjects;
    }

    public void addModel(ManipulationModel manipulationModel) {
        rootModel = manipulationModel;
    }

    public ManipulationModel getModel() {
        return rootModel;
    }

    public Project getProject() {
        return project;
    }
}
