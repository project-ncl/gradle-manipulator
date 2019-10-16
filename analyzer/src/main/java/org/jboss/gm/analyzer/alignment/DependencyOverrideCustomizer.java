package org.jboss.gm.analyzer.alignment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.gm.analyzer.alignment.AlignmentService.Response;
import org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer;
import org.jboss.gm.analyzer.alignment.util.DependencyPropertyParser;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.GMLogger;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * {@link ResponseCustomizer} that changes the versions of
 * aligned dependencies
 *
 * The implementation is very simple and takes Map as a constructor argument and uses the map keys to check
 * if a dependency matches. If so, the map's value is used as the new version
 *
 * TODO: figure out if we need to worry about order
 */
public class DependencyOverrideCustomizer implements ResponseCustomizer {

    private static final Logger logger = GMLogger.getLogger(DependencyExclusionCustomizer.class);

    private final Map<ProjectRef, String> overrideMap;

    public DependencyOverrideCustomizer(Map<ProjectRef, String> overrideMap) {
        this.overrideMap = overrideMap;
    }

    @Override
    public Response customize(Response response) {

        response.setOverrideMap(overrideMap);

        return response;
    }

    public static ResponseCustomizer fromConfigurationForModule(Configuration configuration,
            Set<Project> projects) {

        DependencyOverrideCustomizer result = null;
        final Map<ProjectRef, String> overrideMap = new LinkedHashMap<>();
        final Map<String, String> prefixed = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                "dependencyOverride.");

        if (!prefixed.isEmpty()) {
            //the idea is to create one DependencyOverrideCustomizer per configuration property
            for (String key : prefixed.keySet()) {
                final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);

                for (Project project : projects) {
                    if (isNotEmpty(project.getVersion().toString()) &&
                            isNotEmpty(project.getGroup().toString()) &&
                            isNotEmpty(project.getName())) {

                        final ProjectVersionRef projectRef = new SimpleProjectVersionRef(project.getGroup().toString(),
                                project.getName(), project.getVersion().toString());
                        if (keyParseResult.matchesModule(projectRef)) {
                            final String overrideVersion = prefixed.get(key);
                            logger.debug("Overriding dependency {} from in module {} with version {}",
                                    keyParseResult.getDependency(), projectRef, overrideVersion);
                            overrideMap.put(keyParseResult.getDependency(), overrideVersion);
                        }
                    }
                }
            }
        }

        if (!overrideMap.isEmpty()) {
            logger.debug("Returning overrideMap of {} ", overrideMap);
            result = new DependencyOverrideCustomizer(overrideMap);
        }
        return result;
    }
}
