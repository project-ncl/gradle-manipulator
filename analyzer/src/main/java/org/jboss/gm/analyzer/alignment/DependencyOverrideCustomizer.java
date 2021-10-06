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
import org.jboss.gm.common.utils.ProjectUtils;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * {@link ResponseCustomizer} that changes the versions of aligned dependencies.
 * <p>
 * The implementation is very simple and takes Map as a constructor argument and uses the map keys to check if a
 * dependency matches. If so, the map's value is used as the new version.
 */
// TODO: figure out if we need to worry about order
public class DependencyOverrideCustomizer implements ResponseCustomizer {

    private static final Logger logger = GMLogger.getLogger(DependencyOverrideCustomizer.class);

    private final Map<ProjectRef, String> overrideMap;

    /**
     * Creates a new dependency override customizer with the given override map.
     *
     * @param overrideMap the map to use to check if a dependency matches
     */
    public DependencyOverrideCustomizer(Map<ProjectRef, String> overrideMap) {
        this.overrideMap = overrideMap;
    }

    @Override
    public Response customize(Response response) {

        response.setOverrideMap(overrideMap);

        return response;
    }

    /**
     * This is created by the {@link AlignmentServiceFactory} when creating the request/response customizers.
     *
     * @param configuration the Configuration object
     * @param projects the collection of projects
     * @return an initiated ResponseCustomizer
     */
    public static ResponseCustomizer fromConfigurationForModule(Configuration configuration,
            Set<Project> projects) {

        DependencyOverrideCustomizer result = null;
        final Map<ProjectRef, String> overrideMap = new LinkedHashMap<>();
        final Map<String, String> prefixed = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                "dependencyOverride.");

        if (!prefixed.isEmpty()) {
            for (Map.Entry<String, String> entry : prefixed.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);

                for (Project project : projects) {
                    String group = ProjectUtils.getRealGroupId(project);
                    if (isNotEmpty(project.getVersion().toString()) &&
                            isNotEmpty(group) &&
                            isNotEmpty(project.getName())) {
                        final ProjectVersionRef projectRef = new SimpleProjectVersionRef(group,
                                project.getName(), project.getVersion().toString());
                        if (keyParseResult.matchesModule(projectRef)) {
                            final String overrideVersion = value;
                            logger.debug("Overriding dependency {} in module {} with version {}",
                                    keyParseResult.getDependency(), projectRef, overrideVersion);
                            overrideMap.put(keyParseResult.getDependency(), overrideVersion);
                        }
                    }
                }
            }
        }

        if (!overrideMap.isEmpty()) {
            logger.debug("Returning overrideMap of {}", overrideMap);
            result = new DependencyOverrideCustomizer(overrideMap);
        }
        // If null is returned this is filtered out in AlignmentServiceFactory::geResponseCustomizer with the filter
        return result;
    }
}
