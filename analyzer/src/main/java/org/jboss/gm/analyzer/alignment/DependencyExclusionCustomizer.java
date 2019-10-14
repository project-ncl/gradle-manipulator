package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.RequestCustomizer} that removes dependencies from a
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.Request}
 *
 * The idea is that this class will be created with a predicate (which can of course be the product of multiple predicates)
 * that will match dependencies that are supposed to be excluded.
 * The hard part is creating the proper predicate for each project based on configuration similar to what PME offers
 *
 * TODO: figure out if we need to worry about order
 */
public class DependencyExclusionCustomizer implements AlignmentService.RequestCustomizer {

    private static final Logger log = GMLogger.getLogger(DependencyExclusionCustomizer.class);

    private final Predicate<ProjectVersionRef> predicate;

    public DependencyExclusionCustomizer(Predicate<ProjectVersionRef> predicate) {
        this.predicate = predicate;
    }

    @Override
    public AlignmentService.Request customize(AlignmentService.Request request) {
        final List<? extends ProjectVersionRef> dependenciesWithoutExclusions = request.getDependencies().stream()
                .filter(predicate).collect(Collectors.toList());

        return new AlignmentService.Request(request.getProject(), dependenciesWithoutExclusions);
    }

    public static AlignmentService.RequestCustomizer fromConfigurationForModule(Configuration configuration,
            Set<Project> projects) {

        DependencyExclusionCustomizer result = null;
        final List<Predicate> predicates = new ArrayList<>();
        final Map<String, String> prefixed = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                "dependencyExclusion.");

        if (!prefixed.isEmpty()) {
            //the idea is to start with a predicate that passes all artifacts and add one predicate per configured exclusion
            for (String key : prefixed.keySet()) {
                final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);
                for (Project project : projects) {
                    final ProjectVersionRef projectRef = new SimpleProjectVersionRef(project.getGroup().toString(),
                            project.getName(), project.getVersion().toString());
                    if (keyParseResult.matchesModule(projectRef)) {
                        log.debug("Excluding dependency {} from alignment of module {}", keyParseResult.getDependency(),
                                projectRef);
                        // if the key matches this module, add a predicate that rejects the artifact that was configured in the property
                        predicates.add(new DependencyExclusionPredicate(keyParseResult.getDependency()));
                    }
                }
            }
        }

        if (!predicates.isEmpty()) {
            result = new DependencyExclusionCustomizer(predicates.stream().reduce(x -> true, Predicate::and));
        }
        return result;
    }

    private static class DependencyExclusionPredicate implements Predicate<ProjectRef> {
        private final ProjectRef dependency;

        DependencyExclusionPredicate(ProjectRef dependency) {
            this.dependency = dependency;
        }

        @Override
        public boolean test(ProjectRef gav) {
            return !dependency.matches(gav);
        }
    }
}
