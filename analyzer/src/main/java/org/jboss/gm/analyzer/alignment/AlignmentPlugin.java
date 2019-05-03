package org.jboss.gm.analyzer.alignment;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jboss.gm.common.alignment.ManifestUtils;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.jboss.gm.common.alignment.ManipulationUtils;

/**
 * Results in adding a task with name {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME}.
 * It scans the project(s) and creates the manipulation models.
 */
public class AlignmentPlugin implements Plugin<Project> {

    static {
        System.out.println("Injecting AlignmentPlugin ; version " + ManifestUtils.getManifestInformation());
    }

    @Override
    public void apply(Project project) {

        // we need to create an empty alignment file at the project root
        // this file will then be populated by the alignment task of each project
        if (project.getRootProject() == project) {
            createInitialManipulationModel(project);
        }
        project.getTasks().create(AlignmentTask.NAME, AlignmentTask.class);
    }

    private void createInitialManipulationModel(Project project) {
        project.afterEvaluate(pr -> {
            // these operation need to be performed in afterEvaluate because only then is the group information
            // populated for certain
            ManipulationUtils.addManipulationModel(pr.getRootDir(), getManipulationModel(pr));
        });

    }

    private ManipulationModel getManipulationModel(Project project) {
        final String name = project.getName();
        final ManipulationModel alignmentModel = new ManipulationModel(name, project.getGroup().toString());
        AlignmentTask.projectsToAlign.add(name);
        project.getChildProjects().forEach((n, p) -> alignmentModel.addChild(getManipulationModel(p)));
        return alignmentModel;
    }
}
