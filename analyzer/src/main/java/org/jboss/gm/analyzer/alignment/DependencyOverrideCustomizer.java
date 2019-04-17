package org.jboss.gm.analyzer.alignment;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.commons.configuration2.Configuration;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The idea is that this class will be created with a Map from GAV to predicate and override version.
 * If a GAV matches a predicate then the overridden version is returned
 *
 * TODO: figure out if we need to worry about order
 */
public class DependencyOverrideCustomizer implements AlignmentService.ResponseCustomizer {

	private static final Logger log = LoggerFactory.getLogger(DependencyExclusionCustomizer.class);

	private final Map<ProjectRef, String> overrideMap;

	public DependencyOverrideCustomizer(Map<ProjectRef, String> overrideMap) {
		this.overrideMap = overrideMap;
	}

	@Override
	public AlignmentService.Response customize(AlignmentService.Response response) {
		return new DependencyOverrideCustomizerResponse(overrideMap, response);
	}

	public static AlignmentService.ResponseCustomizer fromConfigurationForModule(Configuration configuration, ProjectRef projectRef) {
		final Configuration dependencyExclusionConfiguration = configuration.subset("dependencyOverride");
		if (dependencyExclusionConfiguration.isEmpty()) {
			return AlignmentService.ResponseCustomizer.NOOP;
		}

		final Map<ProjectRef, String> overrideMap = new LinkedHashMap<>();

		final Iterator<String> keys = dependencyExclusionConfiguration.getKeys();
		//the idea is to create one DependencyOverrideCustomizer per configuration property
		while (keys.hasNext()) {
			final String key = keys.next();

			try {
				final DependencyPropertyParser.Result keyParseResult = DependencyPropertyParser.parse(key);
				if (keyParseResult.matchesModule(projectRef)) {
					final String overrideVersion = dependencyExclusionConfiguration.getString(key);
					log.debug("Overriding dependency {} from in module {} with version {}",
							keyParseResult.getDependency(), projectRef, overrideVersion);
					overrideMap.put(keyParseResult.getDependency(), overrideVersion);
				}
			}
			catch (RuntimeException e) {
				log.debug("Unable to parse key {}", key, e);
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
		public String getAlignedVersionOfGav(GAV gav) {
			final Optional<ProjectRef> projectRef = matchingProjectRef(gav);
			if (projectRef.isPresent()) {
				return overrideMap.get(projectRef.get());
			}

			return gav.getVersion();
		}

		private Optional<ProjectRef> matchingProjectRef(GAV gav) {
			return overrideMap.keySet().stream().filter(p -> p.matches(gav.toProjectVersionRef())).findFirst();
		}
	}

	private static class DependencyOverridePredicate implements Predicate<GAV> {
		private final ProjectRef dependency;

		DependencyOverridePredicate(ProjectRef dependency) {
			this.dependency = dependency;
		}


		@Override
		public boolean test(GAV gav) {
			return !dependency.matches(gav.toProjectVersionRef());
		}
	}

}
