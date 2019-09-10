package org.jboss.gm.analyzer.alignment;

import org.aeonbits.owner.ConfigCache;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.ManipulationCache;
import org.jboss.gm.common.logging.FilteringCustomLogger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.ManifestUtils;

/**
 * Results in adding a task with name {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME}.
 * It scans the project(s) and creates the manipulation models.
 */
@SuppressWarnings("unused")
public class AlignmentPlugin implements Plugin<Project> {

    private final Logger logger = GMLogger.getLogger(getClass());

    static {
        System.out.println("Injecting AlignmentPlugin ; version " + ManifestUtils.getManifestInformation());

        FilteringCustomLogger.enableFilter();
    }

    @Override
    public void apply(Project project) {
        // we need to create an empty alignment file at the project root
        // this file will then be populated by the alignment task of each project
        if (project.getRootProject() == project) {
            project.afterEvaluate(pr -> {
                // Run this in afterEvaluate because only then is the group information populated for certain
                final ManipulationCache cache = ManipulationCache.getCache(project, getManipulationModel(project));

                logger.info("Setup cache for project {}", cache);
            });
        }

        if (System.getProperty("gmeFunctionalTest") != null) {
            ConfigCache.getOrCreate(Configuration.class).reload();
        }

        project.getTasks().create(AlignmentTask.NAME, AlignmentTask.class);
    }

    private ManipulationModel getManipulationModel(Project project) {
        final String name = project.getName();
        final ManipulationCache cache = ManipulationCache.getCache(project);
        final ManipulationModel alignmentModel = new ManipulationModel(name, project.getGroup().toString());

        cache.addProject(name);
        project.getChildProjects().forEach((n, p) -> alignmentModel.addChild(getManipulationModel(p)));

        return alignmentModel;
    }
}
