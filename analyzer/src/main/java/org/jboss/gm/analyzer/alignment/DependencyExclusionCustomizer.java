package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.Configuration;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The idea is that this class will be created with a predicate (which can of course be the product of multiple predicates)
 * that will match dependencies that are supposed to be excluded.
 * The hard part is creating the proper predicate for each project based on configuration similar to what PME offers
 *
 * TODO: figure out if we need to worry about order
 */
public class DependencyExclusionCustomizer implements AlignmentService.RequestCustomizer {

	private static final Logger log = LoggerFactory.getLogger(DependencyExclusionCustomizer.class);

	private final Predicate<GAV> predicate;

	public DependencyExclusionCustomizer(Predicate<GAV> predicate) {
		this.predicate = predicate;
	}

	@Override
	public AlignmentService.Request customize(AlignmentService.Request request) {
		final GAV project = request.getProject();
		final List<? extends GAV> dependenciesWithoutExclusions =
				request.getDependencies().stream().filter(predicate).collect(Collectors.toList());

		return new AlignmentService.Request(project, dependenciesWithoutExclusions);
	}

	public static AlignmentService.RequestCustomizer fromConfigurationForModule(Configuration configuration, ProjectRef projectRef) {
		final Configuration dependencyExclusionConfiguration = configuration.subset("dependencyExclusion");
		if (dependencyExclusionConfiguration.isEmpty()) {
			return AlignmentService.RequestCustomizer.NOOP;
		}

		final Iterator<String> keys = dependencyExclusionConfiguration.getKeys();
		//the idea is to start with a predicate that passes all artifacts and add one predicate per configured exclusion
		final List<Predicate> predicates = new ArrayList<>();
		while (keys.hasNext()) {
			final String key = keys.next();

			try {
				final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);
				if (keyParseResult.matchesModule(projectRef)) {
					log.debug("Excluding dependency {} from alignment of module {}", keyParseResult.getDependency(), projectRef);
					// if the key matches this module, add a predicate that rejects the artifact that was configured in the property
					predicates.add(new DependencyExclusionPredicate(keyParseResult.getDependency()));
				}
			}
			catch (RuntimeException e) {
				log.debug("Unable to parse key {}", key, e);
			}
		}

		if (predicates.isEmpty()) {
			return AlignmentService.RequestCustomizer.NOOP;
		}

		return new DependencyExclusionCustomizer(predicates.stream().reduce(x->true, Predicate::and));
	}

	private static class DependencyExclusionPredicate implements Predicate<GAV> {
		private final ProjectRef dependency;

		DependencyExclusionPredicate(ProjectRef dependency) {
			this.dependency = dependency;
		}


		@Override
		public boolean test(GAV gav) {
			return !dependency.matches(gav.toProjectVersionRef());
		}
	}
}
