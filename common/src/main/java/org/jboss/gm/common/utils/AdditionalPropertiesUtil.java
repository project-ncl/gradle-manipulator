package org.jboss.gm.common.utils;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;

public final class AdditionalPropertiesUtil {

    private AdditionalPropertiesUtil() {
    }

    public static Object getProperty(Project project, String name) {
        return project.getRootProject().getExtensions().getExtraProperties().get(name);
    }

    public static void setPropertyIfMissing(Project project, String name, Object value) {
        final ExtraPropertiesExtension extraProperties = project.getRootProject().getExtensions().getExtraProperties();
        if (!extraProperties.has(name)) {
            extraProperties.set(name, value);
        }
    }
}
