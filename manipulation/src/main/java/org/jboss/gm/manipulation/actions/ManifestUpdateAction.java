package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.plugins.osgi.OsgiManifest;
import org.gradle.api.tasks.bundling.Jar;
import org.jboss.gm.common.alignment.Module;

/**
 * Overrides specified OSGI manifest entries.
 * <p>
 * Currently only overrides manifest of type OsgiManifest and only entries "Specification-Version" and
 * "Implementation-Version".
 */
public class ManifestUpdateAction implements Action<Project> {

    private Module alignmentConfiguration;

    public ManifestUpdateAction(Module alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @Override
    public void execute(Project project) {
        project.getTasks().withType(Jar.class, jar -> {
            if (jar.getManifest() instanceof OsgiManifest) {
                OsgiManifest manifest = (OsgiManifest) jar.getManifest();
                if (manifest.getInstructions().containsKey("Implementation-Version")) {
                    manifest.instructionReplace("Implementation-Version", alignmentConfiguration.getVersion());
                }
                if (manifest.getInstructions().containsKey("Specification-Version")) {
                    manifest.instructionReplace("Specification-Version", alignmentConfiguration.getVersion());
                }
            } else if (jar.getManifest() instanceof DefaultManifest) {
                // TODO what are common entries here?
            }
        });
    }
}
