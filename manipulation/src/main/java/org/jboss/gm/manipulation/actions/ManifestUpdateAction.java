package org.jboss.gm.manipulation.actions;

import java.io.ByteArrayOutputStream;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.bundling.Jar;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Overrides specified Manifest entries.
 * <p>
 * Currently only overrides manifest entries "Specification-Version" and "Implementation-Version".
 */
public class ManifestUpdateAction implements Action<Project> {

    private final Logger logger = GMLogger.getLogger(getClass());

    private final ManipulationModel alignmentConfiguration;

    public ManifestUpdateAction(ManipulationModel alignmentConfiguration) {
        this.alignmentConfiguration = alignmentConfiguration;
    }

    @Override
    public void execute(Project project) {
        project.getTasks().withType(Jar.class, jar -> {

            Manifest manifest = jar.getManifest();

            if (manifest == null) {
                logger.debug("Manifest is not defined for project {}", project.getName());
                return;
            }
            if (manifest.getAttributes().containsKey("Implementation-Version")) {
                manifest.getAttributes().put("Implementation-Version", alignmentConfiguration.getVersion());
            }
            if (manifest.getAttributes().containsKey("Specification-Version")) {
                manifest.getAttributes().put("Specification-Version", alignmentConfiguration.getVersion());
            }
            if (manifest instanceof DefaultManifest) {
                // TODO: what are common entries here?
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ((DefaultManifest) manifest).writeTo(byteArrayOutputStream);
                logger.warn("NYI : Found DefaultManifest with current entries: {}",
                        byteArrayOutputStream.toString());
            }
        });
    }
}
