package org.jboss.gm.analyzer.alignment;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The idea is that this class will be created with a predicate (which can of course be the product of multiple predicates)
 * that will match dependencies that are supposed to be excluded.
 * The hard part is creating the proper predicate for each project based on configuration similar to what PME offers
 */
public class DependencyExclusionCustomizer implements AlignmentService.RequestCustomizer {

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

}
