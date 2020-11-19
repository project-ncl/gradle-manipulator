package org.jboss.gm.manipulation.actions;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang.reflect.MethodUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.bundling.Jar;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Overrides specified Manifest entries.
 */
public class ManifestUpdateAction implements Action<Project> {

    private static final String[] manifestOverwriteValues = new String[] {
            "Implementation-Version",
            "Specification-Version",
            "Bundle-Version",
    };
    private static final String[] manifestOptionalNameValues = new String[] { "Implementation-Title", "Specification-Title",
            "Bundle-Name" };
    private static final String[] manifestOptionalGroupValues = new String[] { "Specification-Vendor", "Implementation-Vendor",
            "Implementation-Vendor-Id" };

    private final Logger logger = GMLogger.getLogger(getClass());

    private final ManipulationModel alignmentModel;

    public ManifestUpdateAction(ManipulationModel alignmentModel) {
        this.alignmentModel = alignmentModel;
    }

    /**
     * Used to override / supplement certain values within the final MANIFEST.MF file.
     * We currently update / or add the following
     * <ul>
     * <li>Implementation-Version</li>
     * <li>Specification-Version</li>
     * <li>Bundle-Version</li>
     * <li>Build-Jdk</li>
     * </ul>
     * Set if not present:
     * <ul>
     * <li>Implementation-Title</li>
     * <li>Specification-Title</li>
     * <li>Specification-Vendor</li>
     * <li>Implementation-Vendor</li>
     * <li>Implementation-Vendor-Id</li>
     * <li>Bundle-Name</li>
     * </ul>
     *
     * @param project the current Gradle project
     */
    @Override
    public void execute(Project project) {

        project.getTasks().withType(Jar.class, j -> {

            @SuppressWarnings("UnstableApiUsage")
            Manifest manifest = j.getManifest();

            if (manifest == null) {
                logger.debug("Manifest is not defined for project {}", project.getName());
                return;
            }

            try {
                // This class was removed in Gradle >= 6 so we need to reflectively invoke it to
                // avoid issues when running under later Gradle versions.
                Class<?> osgi = Class.forName("org.gradle.api.plugins.osgi.OsgiManifest");
                if (osgi.isInstance(manifest)) {
                    logger.debug("Detected OsgiManifest");
                    MethodUtils.invokeExactMethod(osgi.cast(manifest), "setVersion", alignmentModel.getVersion());
                }
            } catch (ClassNotFoundException e) {
                // TODO: Sanity check on Gradle version?
                logger.debug("OsgiManifest does not exist");
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new ManipulationUncheckedException("Unable to examine OsgiManifest", e);
            }

            for (String e : manifestOverwriteValues) {
                if (manifest.getAttributes().containsKey(e)) {
                    logger.warn("For task {}, overriding {} value {} with {}", j.getName(), e,
                            manifest.getAttributes().get(e),
                            alignmentModel.getVersion());
                }
                manifest.getAttributes().put(e, alignmentModel.getVersion());
            }
            for (String e : manifestOptionalNameValues) {
                if (!manifest.getAttributes().containsKey(e)) {
                    logger.info("For task {}, adding {} value with {}", j.getName(), e, alignmentModel.getName());
                    manifest.getAttributes().put(e, alignmentModel.getName());
                }
            }
            if (isNotEmpty(alignmentModel.getGroup())) {
                for (String e : manifestOptionalGroupValues) {
                    if (!manifest.getAttributes().containsKey(e)) {
                        logger.info("For task {}, adding {} value with {}", j.getName(), e, alignmentModel.getGroup());
                        manifest.getAttributes().put(e, alignmentModel.getGroup());
                    }
                }
            }

            String exportPackage = "Export-Package";
            if (manifest.getAttributes().containsKey(exportPackage)) {
                logger.info("For task {}, updating Export-Package version {} to {}", j.getName(),
                        alignmentModel.getOriginalVersion(),
                        alignmentModel.getVersion());
                manifest.getAttributes()
                        .put(exportPackage, (manifest.getAttributes().get(exportPackage).toString()).replaceAll(
                                "version=\"?" + alignmentModel.getOriginalVersion() + "\"?",
                                "version=\"" + alignmentModel.getVersion() + '"'));
            }

            manifest.getAttributes().put("Build-Jdk", System.getProperty("java.version"));
        });
    }
}
