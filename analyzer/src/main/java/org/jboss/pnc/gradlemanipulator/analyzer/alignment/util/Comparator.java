package org.jboss.pnc.gradlemanipulator.analyzer.alignment.util;

import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;

/**
 * Utility class for comparing dependencies.
 */
@UtilityClass
public class Comparator {
    /**
     * Determines whether the set contains the GAV from the {@link UnresolvedDependency}.
     *
     * @param projectDependencies the set of dependencies of examine
     * @param unresolvedDependency the UnresolvedDependency to verify
     * @return true if it does match
     */
    public static boolean contains(
            Set<ProjectDependency> projectDependencies,
            UnresolvedDependency unresolvedDependency) {
        ModuleVersionSelector moduleVersionSelector = unresolvedDependency.getSelector();
        for (ProjectDependency projectDependency : projectDependencies) {
            if (StringUtils.equals(moduleVersionSelector.getGroup(), projectDependency.getGroup()) &&
                    StringUtils.equals(moduleVersionSelector.getName(), projectDependency.getName()) &&
                    StringUtils.equals(moduleVersionSelector.getVersion(), projectDependency.getVersion())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether the set contains the GAV from the {@link ResolvedDependency}.
     *
     * @param projectDependencies the set of dependencies of examine
     * @param dependency the ResolvedDependency to verify
     * @return true if it does match
     */
    public static boolean contains(Set<ProjectDependency> projectDependencies, ResolvedDependency dependency) {
        for (ProjectDependency projectDependency : projectDependencies) {
            if (StringUtils.equals(dependency.getModuleGroup(), projectDependency.getGroup()) &&
                    StringUtils.equals(dependency.getModuleName(), projectDependency.getName()) &&
                    StringUtils.equals(dependency.getModuleVersion(), projectDependency.getVersion())) {
                return true;
            }
        }
        return false;
    }
}
