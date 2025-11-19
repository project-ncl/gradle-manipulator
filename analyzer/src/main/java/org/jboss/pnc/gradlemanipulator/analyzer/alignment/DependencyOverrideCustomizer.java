package org.jboss.pnc.gradlemanipulator.analyzer.alignment;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentService.Manipulator;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.AlignmentService.Response;
import org.jboss.pnc.gradlemanipulator.analyzer.alignment.util.DependencyPropertyParser;
import org.jboss.pnc.gradlemanipulator.common.Configuration;
import org.jboss.pnc.gradlemanipulator.common.logging.GMLogger;
import org.jboss.pnc.gradlemanipulator.common.utils.ProjectUtils;
import org.jboss.pnc.mavenmanipulator.core.util.PropertiesUtils;

/**
 * {@link Manipulator} that changes the versions of aligned dependencies.
 * <p>
 * The implementation stores the configuration and current projects and uses those
 * parsing out the dependencyOverride to check if a dependency matches.
 * If so, the map's value is used as the new version (which may be empty,
 * functioning as an exclusion)
 */
public class DependencyOverrideCustomizer implements Manipulator {

    public static final String DEPENDENCY_OVERRIDE = "dependencyOverride.";

    private final Logger logger = GMLogger.getLogger(DependencyOverrideCustomizer.class);

    private final Set<Project> projects;
    private final Configuration configuration;

    public DependencyOverrideCustomizer(Configuration configuration, Set<Project> projects) {
        this.configuration = configuration;
        this.projects = projects;
    }

    @Override
    public void customize(Response response) {
        final Map<Project, Map<ProjectRef, String>> dependencyOverrides = new LinkedHashMap<>();
        final Map<String, String> prefixed = PropertiesUtils.getPropertiesByPrefix(
                configuration.getProperties(),
                DEPENDENCY_OVERRIDE);

        if (!prefixed.isEmpty()) {
            for (Map.Entry<String, String> entry : prefixed.entrySet()) {
                final String key = entry.getKey();
                final String overrideVersion = entry.getValue();
                final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);

                for (Project project : projects) {
                    Map<ProjectRef, String> overrideMap = dependencyOverrides
                            .getOrDefault(project, new LinkedHashMap<>());
                    String group = ProjectUtils.getRealGroupId(project);
                    if (isNotEmpty(project.getVersion().toString()) &&
                            isNotEmpty(group) &&
                            isNotEmpty(project.getName())) {
                        final ProjectVersionRef projectRef = new SimpleProjectVersionRef(
                                group,
                                project.getName(),
                                project.getVersion().toString());
                        if (keyParseResult.matchesModule(projectRef)) {
                            logger.debug(
                                    "Overriding dependency {} in module {} with version '{}'",
                                    keyParseResult.getDependency(),
                                    projectRef,
                                    overrideVersion);
                            overrideMap.put(keyParseResult.getDependency(), overrideVersion);
                        }
                    }
                    dependencyOverrides.put(project, overrideMap);
                }
            }
        }

        logger.debug("Setting overrideMap to {}", dependencyOverrides);
        response.setDependencyOverrides(dependencyOverrides);
    }
}
