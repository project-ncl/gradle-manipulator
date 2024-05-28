package org.jboss.gm.analyzer.alignment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.io.rest.RestException;
import org.commonjava.maven.ext.io.rest.Translator;
import org.gradle.api.logging.LogLevel;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.FilteringCustomLogger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.utils.RESTUtils;
import org.slf4j.Logger;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.commonjava.maven.ext.core.state.DependencyState.DependencyPrecedence.NONE;

/**
 * An implementation of {@link AlignmentService} that uses the Dependency Analyzer service
 * in order to get the proper aligned versions of dependencies (as well as the version
 * of the project itself). The heavy lifting is done by
 * {@link org.commonjava.maven.ext.io.rest.DefaultTranslator}.
 */
public class DAAlignmentService implements AlignmentService {

    private final Logger logger = GMLogger.getLogger(getClass());

    private final Translator restEndpoint;

    private final DependencyState.DependencyPrecedence dependencySource;

    private final String endpointUrl;

    private final boolean versionModification;

    /**
     * Constructs a new Dependency Analyzer service with the given configuration.
     *
     * @param configuration holds all configuration values for the plugins
     */
    public DAAlignmentService(Configuration configuration) {
        dependencySource = configuration.dependencyConfiguration();
        endpointUrl = configuration.daEndpoint();
        versionModification = configuration.versionModificationEnabled();

        logger.debug("endpointUrl = {}, dependencySource = {}", endpointUrl, dependencySource);

        if (isEmpty(endpointUrl) && dependencySource != NONE) {
            throw new ManipulationUncheckedException("'{}' must be configured in order for dependency scanning to work",
                    Configuration.DA);
        }

        restEndpoint = RESTUtils.getTranslator(configuration);
    }

    /**
     * Performs the alignment of the given request.
     *
     * @param request both the collected project dependencies GAVs and the project GAVs
     * @return the resulting aligned dependencies from the dependency analyzer
     * @throws RestException if an error occurs looking up the versions.
     */
    @Override
    public Response align(AlignmentService.Request request) throws RestException {
        if (isEmpty(endpointUrl)) {
            logger.warn("No restUrl configured ; unable to call endpoint");
            return new Response(Collections.emptyMap());
        }
        final LogLevel originalLevel = FilteringCustomLogger.getContext().getLevel();
        final List<ProjectVersionRef> vParams = request.getDependencies();
        final Map<ProjectVersionRef, String> vMap;

        if (dependencySource == NONE) {
            logger.warn("No dependencySource configured ; unable pass GAVs into endpoint");
            vMap = new HashMap<>();
        } else {
            logger.info("Passing {} GAVs into the REST client api {}", vParams.size(), vParams);
            try {
                if (originalLevel == LogLevel.LIFECYCLE) {
                    FilteringCustomLogger.getContext().setLevel(LogLevel.INFO);
                }
                vMap = restEndpoint.lookupVersions(vParams);
            } finally {
                FilteringCustomLogger.getContext().setLevel(originalLevel);
            }
            logger.info("REST Client returned: {}", vMap);
        }
        final Response response = new Response(vMap);

        final List<ProjectVersionRef> pParams = request.getProject();

        if (versionModification && !pParams.isEmpty()) {
            logger.debug("Passing {} project GAVs into the REST client api {}", pParams.size(), pParams);

            final Map<ProjectVersionRef, String> pMap;
            try {
                if (originalLevel == LogLevel.LIFECYCLE) {
                    FilteringCustomLogger.getContext().setLevel(LogLevel.INFO);
                }
                pMap = restEndpoint.lookupProjectVersions(pParams);
            } finally {
                FilteringCustomLogger.getContext().setLevel(originalLevel);
            }

            logger.info("REST Client returned for project versions: {}", pMap);

            final ProjectVersionRef projectVersion = pParams.get(0);
            final String newProjectVersion = pMap.get(projectVersion);

            logger.info("Retrieving project version {} and returning {}", projectVersion, newProjectVersion);

            response.getTranslationMap().putAll(pMap);
        }

        return response;
    }
}
