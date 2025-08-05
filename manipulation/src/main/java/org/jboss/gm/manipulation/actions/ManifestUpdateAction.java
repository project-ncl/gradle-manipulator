package org.jboss.gm.manipulation.actions;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.reflect.MethodUtils;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.bundling.Jar;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;

/**
 * Overrides specified Manifest entries.
 */
public class ManifestUpdateAction implements Action<Project> {

    private static final String[] manifestOverwriteValues = new String[] {
            "Implementation-Version",
            "Specification-Version",
            "Bundle-Version",
    };
    private static final String[] manifestOptionalNameValues = new String[] {
            "Implementation-Title",
            "Specification-Title",
            "Bundle-Name" };
    private static final String[] manifestOptionalGroupValues = new String[] {
            "Specification-Vendor",
            "Implementation-Vendor",
            "Implementation-Vendor-Id" };

    private final Logger logger = GMLogger.getLogger(getClass());

    private final ManipulationModel alignmentModel;

    /**
     * Creates a new manifest update action with the specified alignment model.
     *
     * @param alignmentModel the alignment model
     */
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

        project.getTasks().withType(Jar.class).configureEach(jar -> {

            @SuppressWarnings("UnstableApiUsage")
            Manifest manifest = jar.getManifest();

            if (manifest == null) {
                logger.debug("Manifest is not defined for project {}", project.getName());
                return;
            }

            try {
                // This class was removed in Gradle >= 6, so we need to reflectively invoke it to
                // avoid issues when running under later Gradle versions.
                Class<?> osgi = Class.forName("org.gradle.api.plugins.osgi.OsgiManifest");
                if (osgi.isInstance(manifest)) {
                    logger.debug("Detected OsgiManifest");
                    MethodUtils.invokeExactMethod(
                            osgi.cast(manifest),
                            "setVersion",
                            alignmentModel.getVersion());
                }
            } catch (ClassNotFoundException e) {
                // TODO: Sanity check on Gradle version?
                logger.debug("OsgiManifest does not exist");
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new ManipulationUncheckedException("Unable to examine OsgiManifest", e);
            }

            for (String key : manifestOverwriteValues) {
                if (manifest.getAttributes().containsKey(key)) {
                    Object value = manifest.getAttributes().get(key);

                    if (!value.toString().equals(alignmentModel.getVersion())) {
                        logger.warn(
                                "For project {}, task {}, overriding {} value {} with {}",
                                project.getName(),
                                jar.getName(),
                                key,
                                value,
                                alignmentModel.getVersion());
                        manifest.getAttributes().put(key, alignmentModel.getVersion());
                    } else {
                        logger.info(
                                "For project {}, task {}, not overriding value {} since version ({}) has not changed",
                                project.getName(),
                                jar.getName(),
                                key,
                                alignmentModel.getVersion());
                    }
                } else {
                    logger.info(
                            "For project {}, task {}, adding {} value with version {}",
                            project.getName(),
                            jar.getName(),
                            key,
                            alignmentModel.getVersion());
                    manifest.getAttributes().put(key, alignmentModel.getVersion());
                }
            }

            for (String key : manifestOptionalNameValues) {
                if (!manifest.getAttributes().containsKey(key)) {
                    logger.info(
                            "For project {}, task {}, adding {} value with artifactId {}",
                            project.getName(),
                            jar.getName(),
                            key,
                            alignmentModel.getName());
                    manifest.getAttributes().put(key, alignmentModel.getName());
                }
            }

            if (isNotEmpty(alignmentModel.getGroup())) {
                for (String key : manifestOptionalGroupValues) {
                    if (!manifest.getAttributes().containsKey(key)) {
                        logger.info(
                                "For project {}, task {}, adding {} value with groupId {}",
                                project.getName(),
                                jar.getName(),
                                key,
                                alignmentModel.getGroup());
                        manifest.getAttributes().put(key, alignmentModel.getGroup());
                    }
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info(
                            "For project {}, task {}, not adding {} since groupId is empty",
                            project.getName(),
                            jar.getName(),
                            Arrays.asList(manifestOptionalGroupValues));
                }
            }

            String exportPackage = "Export-Package";
            if (manifest.getAttributes().containsKey(exportPackage)) {
                String exportContents = manifest.getAttributes().get(exportPackage).toString();
                Pattern pattern = Pattern.compile(".*version=\"?([\\w-\\\\.]+)\"?");
                Matcher matcher = pattern.matcher(exportContents);
                if (matcher.find()) {
                    if (!alignmentModel.getVersion().equals(matcher.group(1))) {
                        logger.info(
                                "For project {}, task {}, updating Export-Package version {} to {} (old version {})",
                                project.getName(),
                                jar.getName(),
                                matcher.group(1),
                                alignmentModel.getVersion(),
                                alignmentModel.getOriginalVersion());
                        manifest.getAttributes()
                                .put(
                                        exportPackage,
                                        exportContents.replaceAll(
                                                "version=\"?" + matcher.group(1) + "\"?",
                                                "version=\"" + alignmentModel.getVersion() + '"'));
                    } else {
                        logger.info(
                                "For project {}, task {}, not updating Export-Package since version ({}) has not changed",
                                project.getName(),
                                jar.getName(),
                                matcher.group(1));
                    }
                } else {
                    logger.warn(
                            "For project {}, task {}, not updating Export-Package as unable to match regex in {}",
                            project.getName(),
                            jar.getName(),
                            exportContents);
                }
            }

            manifest.getAttributes().put("Build-Jdk", System.getProperty("java.version"));
        });
    }
}
