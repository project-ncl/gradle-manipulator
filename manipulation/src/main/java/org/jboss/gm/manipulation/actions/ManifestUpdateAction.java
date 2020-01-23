package org.jboss.gm.manipulation.actions;

import java.io.ByteArrayOutputStream;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.osgi.OsgiManifest;
import org.gradle.api.tasks.bundling.Jar;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Overrides specified OSGI manifest entries.
 * <p>
 * Currently only overrides manifest of type OsgiManifest and only entries "Specification-Version" and
 * "Implementation-Version".
 */
public class ManifestUpdateAction implements Action<Project> {

    private final Logger logger = GMLogger.getLogger(getClass());

    private ManipulationModel alignmentConfiguration;

    public ManifestUpdateAction(ManipulationModel alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void execute(Project project) {
        project.getTasks().withType(Jar.class, jar -> {
            if (jar.getManifest() == null) {
                logger.debug("Manifest is not defined for project {}", project.getName());
                return;
            }
            // always change the implementation version if it exists
            if (jar.getManifest().getAttributes().containsKey("Implementation-Version")) {
                jar.getManifest().getAttributes().put("Implementation-Version", alignmentConfiguration.getVersion());
            }
            if (jar.getManifest() instanceof OsgiManifest) {
                OsgiManifest manifest = (OsgiManifest) jar.getManifest();
                if (manifest.getInstructions().containsKey("Implementation-Version")) {
                    manifest.instructionReplace("Implementation-Version", alignmentConfiguration.getVersion());
                }
                if (manifest.getInstructions().containsKey("Specification-Version")) {
                    manifest.instructionReplace("Specification-Version", alignmentConfiguration.getVersion());
                }
            } else if (jar.getManifest() instanceof DefaultManifest) {
                // TODO: what are common entries here?
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ((DefaultManifest) jar.getManifest()).writeTo(byteArrayOutputStream);
                logger.warn("NYI : Found DefaultManifest with current entries: {}",
                        byteArrayOutputStream.toString());
            }
        });
    }
}
