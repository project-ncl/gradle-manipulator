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
import org.jboss.gm.common.utils.ProjectUtils;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.RequestCustomizer} that removes dependencies from a
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService.Request}.
 * <p>
 * The idea is that this class will be created with a predicate (which can of course be the product of multiple
 * predicates) that will match dependencies that are supposed to be excluded. The hard part is creating the proper
 * predicate for each project based on configuration similar to what PME offers.
 */
//  TODO: figure out if we need to worry about order.
public class DependencyExclusionCustomizer implements AlignmentService.RequestCustomizer {

    private static final Logger logger = GMLogger.getLogger(DependencyExclusionCustomizer.class);

    private final Predicate<ProjectRef> predicate;

    /**
     * Creates a dependency exclusion customizer with the given predicate.
     *
     * @param predicate the predicate (or product of multiple predicates) that will match dependencies to be excluded
     */
    public DependencyExclusionCustomizer(Predicate<ProjectRef> predicate) {
        this.predicate = predicate;
    }

    @Override
    public AlignmentService.Request customize(AlignmentService.Request request) {
        final List<ProjectVersionRef> dependenciesWithoutExclusions = request.getDependencies().stream()
                .filter(predicate).collect(Collectors.toList());

        return new AlignmentService.Request(request.getProject(), dependenciesWithoutExclusions);
    }

    /**
     * Creates a request customize from the given configuration and set of projects.
     *
     * @param configuration holds all configuration values for the two plugins
     * @param projects the projects
     * @return the request customizer
     */
    public static AlignmentService.RequestCustomizer fromConfigurationForModule(Configuration configuration,
            Set<Project> projects) {
        final List<Predicate<ProjectRef>> predicates = new ArrayList<>();
        final Map<String, String> prefixed = PropertiesUtils.getPropertiesByPrefix(configuration.getProperties(),
                "dependencyExclusion.");
        DependencyExclusionCustomizer result = null;

        if (!prefixed.isEmpty()) {
            //the idea is to start with a predicate that passes all artifacts and add one predicate per configured exclusion
            for (String key : prefixed.keySet()) {
                final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);
                for (Project project : projects) {
                    String group = ProjectUtils.getRealGroupId(project);
                    if (isNotEmpty(project.getVersion().toString()) &&
                            isNotEmpty(group) &&
                            isNotEmpty(project.getName())) {
                        final ProjectVersionRef projectRef = new SimpleProjectVersionRef(group,
                                project.getName(),
                                project.getVersion().toString());
                        if (keyParseResult.matchesModule(projectRef)) {
                            logger.debug("Excluding dependency {} from alignment of module {}", keyParseResult.getDependency(),
                                    projectRef);
                            // if the key matches this module, add a predicate that rejects the artifact that was configured in the property
                            predicates.add(new DependencyExclusionPredicate(keyParseResult.getDependency()));
                        }
                    }
                }
            }
        }

        if (!predicates.isEmpty()) {
            result = new DependencyExclusionCustomizer(predicates.stream().reduce(x -> true, Predicate::and));
        }
        // If null is returned this is filtered out in AlignmentServiceFactory::getRequestCustomizer with the filter
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
