package org.jboss.gm.analyzer.alignment;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.common.alignment.ManipulationModelCache;

/**
 * Delegates to the additional properties stored in Project
 * This ensures that all tasks will have the same view of the cache
 */
public class AdditionalPropertiesManipulationModelCache implements ManipulationModelCache {

    private static final String NAME_PREFIX = "manipulationModelCache";

    private final Project project;

    public AdditionalPropertiesManipulationModelCache(Project project) {
        this.project = project;
    }

    @Override
    public ManipulationModel get(String name) {
        try {
            return (ManipulationModel) AdditionalPropertiesUtil.getProperty(project, NAME_PREFIX + name);
        } catch (ExtraPropertiesExtension.UnknownPropertyException e) {
            return null;
        }
    }

    @Override
    public void put(String name, ManipulationModel manipulationModel) {
        AdditionalPropertiesUtil.setPropertyIfMissing(project, NAME_PREFIX + name, manipulationModel);
    }
}
