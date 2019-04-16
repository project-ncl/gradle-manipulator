package org.jboss.gm.analyzer.alignment;

import java.util.function.Predicate;

/**
 * The idea is that this class will be created with a predicate
 * that will match dependencies that are supposed to be overridden.
 * It is envisioned that an instance of DependencyOverrideCustomizer will be provided for each piece of dependencyOverride
 * configuration and be passed to WithCustomizersDelegatingAlignmentService
 *
 * TODO: not sure about this idea... it might to simplistic
 */
public class DependencyOverrideCustomizer implements AlignmentService.ResponseCustomizer {

	private final Predicate<GAV> predicate;
	private final String overrideVersion;
	private final int order;

	public DependencyOverrideCustomizer(Predicate<GAV> predicate, String overrideVersion, int order) {
		this.predicate = predicate;
		this.overrideVersion = overrideVersion;
		this.order = order;
	}

	@Override
	public int order() {
		return order;
	}

	@Override
	public AlignmentService.Response customize(AlignmentService.Response response) {
		//TODO: perhaps have a proper solution other than an inner class...
		return new AlignmentService.Response() {
			@Override
			public String getNewProjectVersion() {
				return response.getNewProjectVersion();
			}

			@Override
			public String getAlignedVersionOfGav(GAV gav) {
				if (predicate.test(gav)) {
					return overrideVersion;
				}
				return gav.getVersion();
			}
		};
	}

}
