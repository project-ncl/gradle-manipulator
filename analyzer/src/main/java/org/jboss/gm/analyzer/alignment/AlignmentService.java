package org.jboss.gm.analyzer.alignment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

/**
 * Used by {@link org.jboss.gm.analyzer.alignment.AlignmentTask} in order to perform the alignment
 *
 * @see org.jboss.gm.analyzer.alignment.DAAlignmentService
 */
public interface AlignmentService {

    Response align(Request request) throws ManipulationException;

    /**
     * Contains both the collected project dependencies GAVs and the project GAV.
     * Done this way because currently with {@link org.jboss.gm.analyzer.alignment.DAAlignmentService}
     * we have the ability to get both pieces of information with one call
     *
     * It might make sense to break this up since other types of AlignmentService implementations might
     * not be handle the project GAV
     */
    class Request {
        private final Collection<? extends ProjectVersionRef> dependencies;
        private final List<ProjectVersionRef> project;

        public Request(List<ProjectVersionRef> projectVersionRefs, Collection<? extends ProjectVersionRef> dependencies) {
            this.dependencies = dependencies;
            this.project = projectVersionRefs;
        }

        Collection<? extends ProjectVersionRef> getDependencies() {
            return dependencies;
        }

        List<ProjectVersionRef> getProject() {
            return project;
        }
    }

    /**
     * Contains the resulting aligned dependencies from the dependency analyzer. It will be processed further
     * by the response customizers such as DependencyOverride, ProjectVersionOverride.
     */
    class Response {
        private final Map<ProjectVersionRef, String> translationMap;
        Map<ProjectRef, String> overrideMap;
        String newProjectVersion;

        // Only used by tests.
        Response() {
            this(new HashMap<>());
        }

        Response(Map<ProjectVersionRef, String> translationMap) {
            this.translationMap = translationMap;
        }

        void setNewProjectVersion(String version) {
            newProjectVersion = version;
        }

        void setOverrideMap(Map<ProjectRef, String> overrideMap) {
            this.overrideMap = overrideMap;
        }

        String getNewProjectVersion() {
            return newProjectVersion;
        }

        Map<ProjectVersionRef, String> getTranslationMap() {
            return translationMap;
        }

        String getAlignedVersionOfGav(ProjectVersionRef gav) {
            final Optional<ProjectRef> projectRef = matchingProjectRef(gav);
            if (projectRef.isPresent()) {
                return overrideMap.get(projectRef.get());
            }
            if (translationMap == null) {
                throw new ManipulationUncheckedException("Translation map has not been initialised");
            }
            return translationMap.get(gav);
        }

        private Optional<ProjectRef> matchingProjectRef(ProjectRef gav) {
            return overrideMap == null ? Optional.empty()
                    : overrideMap.keySet().stream().filter(p -> p.matches(gav)).findFirst();
        }
    }

    /**
     * Meant to change {@link org.jboss.gm.analyzer.alignment.AlignmentService.Request} before it is handed to
     * the {@link org.jboss.gm.analyzer.alignment.AlignmentService}
     *
     * @see org.jboss.gm.analyzer.alignment.DependencyExclusionCustomizer
     */
    interface RequestCustomizer {

        Request customize(Request request);

        // Integer.MIN_VALUE is the max order. This means that if we have 2 services for example
        // we with the first one to be invoked before the second, we would give the first one a
        // value for order that is smaller than that of the second one (for example 0 and 10)
        default int order() {
            return 0;
        }
    }

    /**
     * Meant to change {@link org.jboss.gm.analyzer.alignment.AlignmentService.Response} after it is returned by
     * the {@link org.jboss.gm.analyzer.alignment.AlignmentService}
     *
     * @see org.jboss.gm.analyzer.alignment.DependencyOverrideCustomizer
     * @see org.jboss.gm.analyzer.alignment.UpdateProjectVersionCustomizer
     */
    interface ResponseCustomizer {

        Response customize(Response response) throws ManipulationException;

        // Integer.MIN_VALUE is the max order. This means that if we have 2 services for example
        // we with the first one to be invoked before the second, we would give the first one a
        // value for order that is smaller than that of the second one (for example 0 and 10)
        default int order() {
            return 0;
        }
    }
}
