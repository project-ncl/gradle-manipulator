package org.jboss.gm.common.utils;

import lombok.experimental.UtilityClass;

import org.apache.commons.lang.reflect.FieldUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.BasePluginConvention;
import org.jboss.gm.common.logging.GMLogger;

@UtilityClass
public class ProjectUtils {
    private final Logger logger = GMLogger.getLogger(OTELUtils.class);

    /**
     * Gradle annoyingly sets a default group using the following code:
     *
     * <pre>
     *      public Object getGroup() {
     *         if (group != null) {
     *             return group;
     *         } else if (this == rootProject) {
     *             return "";
     *         }
     *         group = rootProject.getName() +
     *              (getParent() == rootProject ? "" : "."
     *                  + getParent().getPath().substring(1).replace(':', '.'));
     *         return group;
     * </pre>
     *
     * We therefore need to reverse that when determining the group for the model as otherwise we could
     * end up with incorrect values which are assumed to be a Maven groupId.
     *
     * @param project the current project
     * @return the group equivalent to a Maven groupId or empty
     */
    public String getRealGroupId(Project project) {
        String group = project.getGroup().toString();
        Project rootProject = project.getRootProject();

        if (project != rootProject) {
            @SuppressWarnings("ConstantConditions")
            String gradleDefaultGroup = rootProject.getName()
                    + (project.getParent() == rootProject ? ""
                            : "."
                                    + project.getParent().getPath().substring(1).replace(':', '.'));
            if (group.equals(gradleDefaultGroup)) {
                return "";
            }
        }
        return group;
    }

    /**
     * The Project name field is private and therefore can't be dynamically updated.
     *
     * @param project the current project
     * @param replacement the new name to use
     */
    public void updateNameField(Project project, Object replacement) {
        try {
            FieldUtils.writeField(project, "name", replacement, true);
        } catch (IllegalAccessException e) {
            throw new ManipulationUncheckedException("Unable to update name field to {}", replacement, e);
        }
    }

    /**
     * This returns the value of archivesBaseName while not returning its default value of project.name
     * archiveTask.archiveBaseName defaults to the project.archivesBaseName which defaults to project.name.
     * References:
     * https://docs.gradle.org/current/userguide/maven_plugin.html
     * https://docs.gradle.org/6.8.1/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html
     *
     * @param project the current project
     * @return the value for the archivesBaseName or null if not set
     */
    public String getArchivesBaseName(Project project) {
        if (project.getConvention().findPlugin(BasePluginConvention.class) != null) {
            String archivesBaseName = project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName();
            if (!project.getName().equals(archivesBaseName)) {
                return archivesBaseName;
            }
        }
        return null;
    }

    /**
     * Use to update the configuration resolution strategy from strict to preferProjectModules to allow GME to update
     * the versions with the strategy causing failures.
     *
     * @param configuration a configuration of a project
     */
    public void updateResolutionStrategy(Configuration configuration) {
        if (configuration.getResolutionStrategy() instanceof DefaultResolutionStrategy) {
            DefaultResolutionStrategy defaultResolutionStrategy = (DefaultResolutionStrategy) configuration
                    .getResolutionStrategy();

            if (defaultResolutionStrategy.getConflictResolution() == ConflictResolution.strict) {
                // failOnVersionConflict() sets this which causes our plugin to crash out. Reset to latest to make an attempt
                // at continuing. As Gradle creates 'decorated', we can't use reflection to change the value
                // back to the default. Therefore, use preferProjectModules as it's not eager-fail.
                logger.debug(
                        "Detected use of conflict resolution strategy strict in {}; resetting to preferProjectModules.",
                        configuration.getName());
                defaultResolutionStrategy.preferProjectModules();
            }
        }
    }
}
