package org.jboss.gm.common.utils;

import lombok.experimental.UtilityClass;

import org.apache.commons.lang.reflect.FieldUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Project;

@UtilityClass
public class ProjectUtils {
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
    public static String getRealGroupId(Project project) {
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
    public static void updateNameField(Project project, Object replacement) {
        try {
            FieldUtils.writeField(project, "name", replacement, true);
        } catch (IllegalAccessException e) {
            throw new ManipulationUncheckedException("Unable to update name field to {}", replacement, e);
        }
    }
}
