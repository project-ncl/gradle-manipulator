package org.jboss.gm.analyzer.alignment;

import java.util.*;
import java.util.stream.Collectors;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;

/**
 * Used by {@link org.jboss.gm.analyzer.alignment.AlignmentTask} in order to perform the alignment
 *
 * @see org.jboss.gm.analyzer.alignment.DAAlignmentService
 */
public interface AlignmentService {

    Response align(Request request);

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
        private final Set<? extends ProjectRef> dynamicDependencies; // dependencies whose version was not set to a specific value, but instead could be a range or latest
        private final List<ProjectVersionRef> project;

        public Request(List<ProjectVersionRef> projectVersionRefs, Collection<? extends ProjectVersionRef> dependencies,
                Set<? extends ProjectRef> dynamicDependencies) {
            this.dependencies = dependencies;
            this.project = projectVersionRefs;
            this.dynamicDependencies = dynamicDependencies;
        }

        Collection<? extends ProjectVersionRef> getDependencies() {
            return dependencies;
        }

        List<ProjectVersionRef> getProject() {
            return project;
        }

        Set<? extends ProjectRef> getDynamicDependencies() {
            return dynamicDependencies;
        }

        Set<? extends ProjectVersionRef> getDynamicDependenciesWithGradleResolvedVersions() {
            final Set<ProjectVersionRef> result = new HashSet<>();
            for (ProjectVersionRef dependency : dependencies) {
                if (dynamicDependencies.contains(dependency.asProjectRef())) {
                    result.add(dependency);
                }
            }
            return result;
        }
    }

    interface Response {

        String getNewProjectVersion();

        Map<ProjectVersionRef, String> getTranslationMap();

        String getAlignedVersionOfGav(ProjectVersionRef gav);
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

        RequestCustomizer NOOP = new RequestCustomizer() {
            @Override
            public Request customize(Request request) {
                return request;
            }

            @Override
            public int order() {
                return Integer.MAX_VALUE;
            }
        };

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
         * No changes to request are allowed - it simply represents the final Request that was sent to the final alignment
         * service
         */
        Response customize(Response response, Request request);

        // Integer.MIN_VALUE is the max order. This means that if we have 2 services for example
        // we with the first one to be invoked before the second, we would give the first one a
        // value for order that is smaller than that of the second one (for example 0 and 10)
        default int order() {
            return 0;
        }

        ResponseCustomizer NOOP = new ResponseCustomizer() {
            @Override
            public Response customize(Response response, Request request) {
                return response;
            }

            @Override
            public int order() {
                return Integer.MAX_VALUE;
            }
        };

    }
}
