package org.jboss.gm.analyzer.alignment;

import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.commonjava.maven.ext.core.state.DependencyState.DependencyPrecedence.NONE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec.binary.Base32;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;
import org.jboss.gm.common.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link org.jboss.gm.analyzer.alignment.AlignmentService} that uses the Dependency Analyzer service
 * in order to get the proper aligned versions of dependencies (as well as the version of the project itself)
 *
 * The heavy lifting is done by {@link org.commonjava.maven.ext.io.rest.DefaultTranslator}
 */
public class DAAlignmentService implements AlignmentService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Translator restEndpoint;

    private final DependencyState.DependencyPrecedence dependencySource;

    public DAAlignmentService(Configuration configuration) {
        final String endpointUrl = configuration.daEndpoint();

        if (endpointUrl == null) {
            throw new ManipulationUncheckedException(
                    String.format("'%s' must be configured in order for model to work", Configuration.DA));
        }

        dependencySource = configuration.dependencyConfiguration();

        restEndpoint = new GradleDefaultTranslator(
                endpointUrl,
                Translator.RestProtocol.CURRENT,
                configuration.restMaxSize(),
                DefaultTranslator.CHUNK_SPLIT_COUNT,
                configuration.restRepositoryGroup(),
                configuration.versionIncrementalSuffix(),
                configuration.logContext());
    }

    @Override
    public Response align(Request request) {
        final List<ProjectVersionRef> translateRequest = new ArrayList<>(request.getDependencies().size() + 1);

        if (dependencySource == NONE) {
            logger.warn("No dependencySource configured ; unable to call endpoint");
            return new Response(request.getProject(), Collections.emptyMap());
        }

        translateRequest.addAll(request.getProject());
        translateRequest.addAll(request.getDependencies());

        logger.debug("Passing {} GAVs following into the REST client api {} ", translateRequest.size(), translateRequest);
        logger.info("Calling REST client with {} GAVS...", translateRequest.size());
        final Map<ProjectVersionRef, String> translationMap = restEndpoint.translateVersions(translateRequest);
        logger.info("REST Client returned {} ", translationMap);

        return new Response(request.getProject(), translationMap);
    }

    private static class Response implements AlignmentService.Response {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private final List<ProjectVersionRef> refOfProject;
        private final Map<ProjectVersionRef, String> translationMap;

        Response(List<ProjectVersionRef> refOfProject, Map<ProjectVersionRef, String> translationMap) {
            this.refOfProject = refOfProject;
            this.translationMap = translationMap;
        }

        // TODO: Verify this is safe - do we need to find the highest projectref version?
        // TODO: When is this used / called ?
        @Override
        public String getNewProjectVersion() {
            logger.info("Retrieving project version {} and returning {} ", refOfProject.get(0),
                    translationMap.get(refOfProject.get(0)));
            return translationMap.get(refOfProject.get(0));
        }

        @Override
        public Map<ProjectVersionRef, String> getTranslationMap() {
            return translationMap;
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            return translationMap.get(gav);
        }
    }

    static class GradleDefaultTranslator extends DefaultTranslator {
        private final Random RANDOM = new Random();

        private final String logContext;

        GradleDefaultTranslator(String endpointUrl, RestProtocol current, int restMaxSize, int restMinSize,
                String repositoryGroup, String incrementalSerialSuffix, String logContext) {
            super(endpointUrl, current, restMaxSize, restMinSize, repositoryGroup, incrementalSerialSuffix);
            this.logContext = logContext;
        }

        @Override
        protected String getHeaderContext() {
            String headerContext;

            if (isNotEmpty(logContext)) {
                headerContext = logContext;
            } else {
                // If we have no MDC PME has been used as the entry point. Dummy one up for DA.
                byte[] randomBytes = new byte[20];
                RANDOM.nextBytes(randomBytes);
                headerContext = "pme-" + new Base32().encodeAsString(randomBytes);
            }

            return headerContext;
        }
    }
}
