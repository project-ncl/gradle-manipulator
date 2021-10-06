package org.jboss.gm.analyzer.alignment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

/**
 * Used by {@link org.jboss.gm.analyzer.alignment.AlignmentTask} in order to perform the alignment.
 *
 * @see org.jboss.gm.analyzer.alignment.DAAlignmentService
 */
public interface AlignmentService {
    /**
     * Performs the alignment of the given request.
     *
     * @param request both the collected project dependencies GAVs and the project GAVs
     * @return the resulting aligned dependencies from the dependency analyzer
     * @throws ManipulationException if an error occurs when looking up GAVs
     */
    Response align(Request request) throws ManipulationException;

    /**
     * Contains both the collected project dependencies GAVs and the project GAVs.
     * <p>
     * Done this way because currently with {@link org.jboss.gm.analyzer.alignment.DAAlignmentService} we have the
     * ability to get both pieces of information with one call.
     * <p>
     * It might make sense to break this up since other types of AlignmentService implementations might not be able to
     * handle the project GAV.
     */
    @Getter
    class Request {
        /**
         * The list of projects.
         *
         * @param project list of the projects
         * @return the list of projects
         */
        private final List<ProjectVersionRef> project;

        /**
         * The list of dependencies.
         *
         * @param dependencies the dependencies
         * @return the list of dependencies
         */
        private final List<ProjectVersionRef> dependencies;

        /**
         * Creates a new request from the given list of projects and the given list of dependencies.
         *
         * @param project the list of the projects
         * @param dependencies the list of dependencies
         */
        public Request(final List<ProjectVersionRef> project, final List<ProjectVersionRef> dependencies) {
            this.project = project;
            this.dependencies = dependencies;
        }
    }

    /**
     * Contains the resulting aligned dependencies from the dependency analyzer. It will be processed further
     * by the response customizers such as DependencyOverride, ProjectVersionOverride.
     */
    @Getter
    @Setter
    class Response {
        /**
         * The translation map.
         *
         * @param translationMap the translation map
         * @return the translation map
         */
        private final Map<ProjectVersionRef, String> translationMap;

        /**
         * The override map.
         *
         * @param overrideMap the override map
         * @return the override map
         */
        private Map<ProjectRef, String> overrideMap;

        /**
         * The new project version.
         *
         * @param newProjectVersion the new project version
         * @return the new project version
         */
        private String newProjectVersion;

        /**
         * Creates a new response from the given translation map.
         *
         * @param translationMap the translation map.
         */
        public Response(final Map<ProjectVersionRef, String> translationMap) {
            this.translationMap = translationMap;
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
        /**
         * Changes the given request before it is handed to the alignment service.
         *
         * @param request the request
         * @return the modified request
         */
        Request customize(Request request);

        /**
         * {@link Integer#MIN_VALUE Integer.MIN_VALUE} is the max order. This means that if we have two services, and we
         * want the first one to be invoked before the second, we would give the first one a value for order that is
         * smaller than that of the second one (for example 0 and 10, respectively).
         *
         * @return the order
         */
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
        /**
         * Changes the response after it is returned by the alignment service.
         *
         * @param response the resulting aligned dependencies from the dependency analyzer
         * @return the resulting aligned dependencies from the dependency analyzer
         * @throws ManipulationException if an error occurs looking up the versions
         */
        Response customize(Response response) throws ManipulationException;

        /**
         * {@link Integer#MIN_VALUE Integer.MIN_VALUE} is the max order. This means that if we have two services, and we
         * want the first one to be invoked before the second, we would give the first one a value for order that is
         * smaller than that of the second one (for example 0 and 10, respectively).
         *
         * @return the order
         */
        default int order() {
            return 0;
        }
    }
}
