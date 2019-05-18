package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.jboss.gm.common.ManipulationModelCache;
import org.jboss.gm.common.model.ManipulationModel;

public final class ManipulationUtils {
    private static final String MANIPULATION_FILE_NAME = "manipulation.json";

    private ManipulationUtils() {
    }

    /**
     * This is meant to be called from as part of a Gradle task that is executed for each project/subproject of the build
     * Is assumes that a task for various projects is never called in parallel
     *
     * @return a valid ManipulationModel.
     */
    public static ManipulationModel getManipulationModel(File rootDir) {
        return getInternalManipulationModel(getManipulationFilePath(rootDir).toFile(), null);
    }

    public static ManipulationModel getManipulationModel(File rootDir, ManipulationModelCache manipulationModelCache) {
        return getInternalManipulationModel(getManipulationFilePath(rootDir).toFile(), manipulationModelCache);
    }

    private static ManipulationModel getInternalManipulationModel(File alignment,
            ManipulationModelCache manipulationModelCache) {
        final String absolutePath = getIdentifierFor(alignment);
        ManipulationModel model = manipulationModelCache == null ? null : manipulationModelCache.get(absolutePath);
        if (model == null) {
            try {
                model = SerializationUtils.getObjectMapper().readValue(alignment, ManipulationModel.class);

                if (manipulationModelCache != null) {
                    manipulationModelCache.put(absolutePath, model);
                }
            } catch (IOException e) {
                throw new ManipulationUncheckedException("Unable to deserialize " + MANIPULATION_FILE_NAME, e);
            }
        }
        return model;
    }

    public static void addManipulationModel(File rootDir, ManipulationModel model,
            ManipulationModelCache manipulationModelCache) {
        manipulationModelCache.put(getIdentifierFor(getManipulationFilePath(rootDir).toFile()), model);
    }

    /**
     * Write the model to disk - override any existing file that might exist
     */
    public static void writeUpdatedManipulationModel(File rootDir, ManipulationModel updatedManipulationModel)
            throws ManipulationException {
        final Path manipulationFilePath = ManipulationUtils.getManipulationFilePath(rootDir);
        try {
            // first delete any existing file
            Files.delete(manipulationFilePath);
        } catch (IOException ignored) {
        }

        try {
            FileUtils.writeStringToFile(
                    manipulationFilePath.toFile(),
                    SerializationUtils.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                            updatedManipulationModel),
                    StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new ManipulationException("Unable to write manipulation.json in project root", e);
        }
    }

    private static String getIdentifierFor(File alignment) {
        return alignment.getAbsolutePath();
    }

    private static Path getManipulationFilePath(File rootDir) {
        return rootDir.toPath().resolve(MANIPULATION_FILE_NAME);
    }
}
