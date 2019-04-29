package org.jboss.gm.analyzer.alignment;

import java.util.Collection;

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
        private final ProjectVersionRef project;

        public Request(ProjectVersionRef project, Collection<? extends ProjectVersionRef> dependencies) {
            this.dependencies = dependencies;
            this.project = project;
        }

        Collection<? extends ProjectVersionRef> getDependencies() {
            return dependencies;
        }

        ProjectVersionRef getProject() {
            return project;
        }
    }

    interface Response {

        String getNewProjectVersion();

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

        Response customize(Response response);

        // Integer.MIN_VALUE is the max order. This means that if we have 2 services for example
        // we with the first one to be invoked before the second, we would give the first one a
        // value for order that is smaller than that of the second one (for example 0 and 10)
        default int order() {
            return 0;
        }

        ResponseCustomizer NOOP = new ResponseCustomizer() {
            @Override
            public Response customize(Response response) {
                return response;
            }

            @Override
            public int order() {
                return Integer.MAX_VALUE;
            }
        };

    }
}
