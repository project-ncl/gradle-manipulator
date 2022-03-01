package org.jboss.gm.analyzer.alignment.util;

import java.util.function.Predicate;

import lombok.Getter;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;

/**
 * Utility class that is meant to parse properties like {@code dependencyOverride} or {@code dependencyExclusion}.
 * <p>
 * See section "Exclusions and Overrides" of
 * https://release-engineering.github.io/pom-manipulation-ext/guide/dep-manip.html.
 */
public final class DependencyPropertyParser {
    private static final Logger logger = GMLogger.getLogger(DependencyPropertyParser.class);

    /**
     * Parses the given property key such as {@code dependencyOverride} or {@code dependencyExclusion}.
     *
     * @param key the property key
     * @return the result containing the dependency
     */
    public static Result parse(String key) {
        if (!key.contains("@")) {
            throw new InvalidUserDataException(
                    "Property '" + key + "' is not a properly formatted key since it does not contain '@'");
        }

        final String[] artifactAndModule = key.split("@");
        if (artifactAndModule.length != 2) {
            throw new InvalidUserDataException(
                    "Property '" + key + "' is not a properly formatted key since it is not properly split by '@'");
        }

        final String[] artifactAndModuleSplit = artifactAndModule[0].split(":");
        if (artifactAndModuleSplit.length != 2) {
            throw new InvalidUserDataException(
                    "Possible missing wildcard. Property '" + key
                            + "' is not a properly formatted key since it is not properly split by '@' and '*");
        }

        logger.warn("### DepPropParse : {} and {}", artifactAndModule[0], artifactAndModule[1]);

        return new ResultImpl(SimpleProjectRef.parse(artifactAndModule[0]), createMatchesModulePredicate(artifactAndModule[1]));
    }

    private static Predicate<ProjectRef> createMatchesModulePredicate(String moduleValue) {
        if ("*".equals(moduleValue)) {
            return (p) -> true;
        }

        final ProjectRef moduleGA = SimpleProjectRef.parse(moduleValue);
        return (p) -> p.matches(moduleGA);
    }

    /**
     * The result of parsing a dependency property.
     */
    public interface Result {
        /**
         * Gets the dependency.
         *
         * @return the dependency
         */
        ProjectRef getDependency();

        /**
         * Returns whether the given module matches.
         *
         * @param projectRef the module
         * @return whether the given module matches
         */
        boolean matchesModule(ProjectRef projectRef);
    }

    @Getter
    private static class ResultImpl implements Result {
        private final ProjectRef dependency;
        private final Predicate<ProjectRef> matchesModulePredicate;

        ResultImpl(ProjectRef dependency, Predicate<ProjectRef> matchesModulePredicate) {
            this.dependency = dependency;
            this.matchesModulePredicate = matchesModulePredicate;
        }

        @Override
        public ProjectRef getDependency() {
            return dependency;
        }

        @Override
        public boolean matchesModule(ProjectRef projectRef) {
            return matchesModulePredicate.test(projectRef);
        }
    }
}
