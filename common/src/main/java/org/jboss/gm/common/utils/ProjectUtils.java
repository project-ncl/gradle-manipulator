package org.jboss.gm.common.utils;

import java.lang.reflect.InvocationTargetException;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.util.GradleVersion;
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
     * This returns the value of archivesName/archivesBaseName while not returning its default value of project.name
     * It defaults to the project.archivesBaseName which defaults to project.name.
     * References:
     * <ul>
     * <li><a href="https://docs.gradle.org/current/userguide/maven_plugin.html">Maven Plugin</a>
     * <li><a href=
     * "https://docs.gradle.org/6.8.1/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html">AbstractArchiveTask</a>
     * </ul>
     *
     * @param project the current project
     * @return the value for the archivesBaseName or null if not set
     */
    @SuppressWarnings({ "unchecked", "deprecation", "UnstableApiUsage" })
    public String getArchivesBaseName(Project project) {
        // See https://docs.gradle.org/8.10.1/userguide/upgrading_version_8.html#deprecated_access_to_conventions
        // The BasePluginExtension was added in 7.1 in https://github.com/gradle/gradle/issues/3425 but the warnings
        // were not added till 8.1 in https://github.com/gradle/gradle/issues/22908
        try {
            if (GradleVersion.current().compareTo(GradleVersion.version("8.1")) >= 0) {
                Class<?> c = Class.forName("org.gradle.api.plugins.BasePluginExtension");
                Object extension = project.getExtensions().findByType(c);
                if (extension != null) {
                    String value = ((Property<String>) c.getMethod("getArchivesName").invoke(extension)).getOrNull();
                    if (!project.getName().equals(value)) {
                        return value;
                    }
                }
            } else {
                // Have to use reflection as convention plugins were removed in Gradle 9
                Class<?> c = Class.forName("org.gradle.api.plugins.BasePluginConvention");
                Object conventionObject = project.getClass().getMethod("getConvention").invoke(project);
                Object basePluginConvention = conventionObject.getClass()
                        .getMethod("findPlugin", Class.class)
                        .invoke(conventionObject, c);
                if (basePluginConvention != null) {
                    String value = (String) basePluginConvention.getClass()
                            .getMethod("getArchivesBaseName")
                            .invoke(basePluginConvention);
                    if (!project.getName().equals(value)) {
                        return value;
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException
                | IllegalAccessException e) {
            logger.error("Unable to invoke getArchivesName method", e);
            throw new ManipulationUncheckedException(e);
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
