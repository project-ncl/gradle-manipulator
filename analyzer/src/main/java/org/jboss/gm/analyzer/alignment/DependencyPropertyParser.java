package org.jboss.gm.analyzer.alignment;

import java.util.function.Predicate;

import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;

/**
 * Meant to parse properties like dependencyOverride and dependencyExclusion
 */
final class DependencyPropertyParser {

    private DependencyPropertyParser() {
    }

    public static Result parse(String key) {
        if (!key.contains("@")) {
            throw new IllegalArgumentException(
                    "Property '" + key + "' is not a properly formatted key since it does not contain '@'");
        }

        final String[] artifactAndModule = key.split("@");
        if (artifactAndModule.length != 2) {
            throw new IllegalArgumentException(
                    "Property '" + key + "' is not a properly formatted key since it is not properly split by '@'");
        }

        return new ResultImpl(SimpleProjectRef.parse(artifactAndModule[0]), createMatchesModulePredicate(artifactAndModule[1]));
    }

    private static Predicate<ProjectRef> createMatchesModulePredicate(String moduleValue) {
        if ("*".equals(moduleValue)) {
            return (p) -> true;
        }

        final ProjectRef moduleGA = SimpleProjectRef.parse(moduleValue);
        return (p) -> p.matches(moduleGA);
    }

    public interface Result {

        ProjectRef getDependency();

        boolean matchesModule(ProjectRef projectRef);
    }

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
