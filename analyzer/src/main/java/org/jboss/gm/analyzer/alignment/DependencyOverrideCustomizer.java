package org.jboss.gm.analyzer.alignment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.gm.analyzer.alignment.util.DependencyPropertyParser;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.GMLogger;

/**
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.ResponseCustomizer} that changes the versions of
 * aligned dependencies
 *
 * The implementation is very simple and takes Map as a constructor argument and uses the map keys to check
 * if a dependency matches. If so, the map's value is used as the new version
 *
 * TODO: figure out if we need to worry about order
 */
public class DependencyOverrideCustomizer implements AlignmentService.ResponseCustomizer {

    private static final Logger log = GMLogger.getLogger(DependencyExclusionCustomizer.class);

    private final Map<ProjectRef, String> overrideMap;

    public DependencyOverrideCustomizer(Map<ProjectRef, String> overrideMap) {
        this.overrideMap = overrideMap;
    }

    @Override
    public AlignmentService.Response customize(AlignmentService.Response response) {
        return new DependencyOverrideCustomizerResponse(overrideMap, response);
    }

    public static AlignmentService.ResponseCustomizer fromConfigurationForModule(Configuration configuration,
            Set<Project> projects) {
        final Map<String, String> prefixed = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                "dependencyOverride.");
        if (prefixed.isEmpty()) {
            return AlignmentService.ResponseCustomizer.NOOP;
        }

        final Map<ProjectRef, String> overrideMap = new LinkedHashMap<>();

        //the idea is to create one DependencyOverrideCustomizer per configuration property
        for (String key : prefixed.keySet()) {
            final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);
            for (Project project : projects) {
                final ProjectVersionRef projectRef = new SimpleProjectVersionRef(project.getGroup().toString(),
                        project.getName(), project.getVersion().toString());
                if (keyParseResult.matchesModule(projectRef)) {
                    final String overrideVersion = prefixed.get(key);
                    log.debug("Overriding dependency {} from in module {} with version {}",
                            keyParseResult.getDependency(), projectRef, overrideVersion);
                    overrideMap.put(keyParseResult.getDependency(), overrideVersion);
                }
            }
        }

        if (overrideMap.isEmpty()) {
            return AlignmentService.ResponseCustomizer.NOOP;
        }

        return new DependencyOverrideCustomizer(overrideMap);
    }

    private static class DependencyOverrideCustomizerResponse implements AlignmentService.Response {

        private final Map<ProjectRef, String> overrideMap;
        private final AlignmentService.Response originalResponse;

        DependencyOverrideCustomizerResponse(Map<ProjectRef, String> overrideMap,
                AlignmentService.Response originalResponse) {
            this.overrideMap = overrideMap;
            this.originalResponse = originalResponse;
        }

        @Override
        public String getNewProjectVersion() {
            return originalResponse.getNewProjectVersion();
        }

        @Override
        public Map<ProjectVersionRef, String> getTranslationMap() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            final Optional<ProjectRef> projectRef = matchingProjectRef(gav);
            if (projectRef.isPresent()) {
                return overrideMap.get(projectRef.get());
            }

            return originalResponse.getAlignedVersionOfGav(gav);
        }

        private Optional<ProjectRef> matchingProjectRef(ProjectRef gav) {
            return overrideMap.keySet().stream().filter(p -> p.matches(gav)).findFirst();
        }
    }
}
