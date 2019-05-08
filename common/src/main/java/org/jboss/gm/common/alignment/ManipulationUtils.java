package org.jboss.gm.common.alignment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

public final class ManipulationUtils {
    private static final String MANIPULATION_FILE_NAME = "manipulation.json";
    private static final Map<String, ManipulationModel> cachedModels = new HashMap<>(7);

    private ManipulationUtils() {
    }

    public static ManipulationModel getManipulationModelAt(File path) throws IOException {
        if (!path.isDirectory()) {
            throw new IOException("Path must be a directory. Was: " + path);
        }
        File alignment = new File(path, MANIPULATION_FILE_NAME);
        return getManipulationModel(alignment);
    }

    private static ManipulationModel getManipulationModel(File alignment) {
        final String absolutePath = getIdentifierFor(alignment);
        ManipulationModel model = cachedModels.get(absolutePath);
        if (model == null) {
            try {
                model = SerializationUtils.getObjectMapper().readValue(alignment, ManipulationModel.class);
                cachedModels.put(absolutePath, model);
            } catch (IOException e) {
                throw new ManipulationUncheckedException("Unable to deserialize " + MANIPULATION_FILE_NAME, e);
            }
        }
        return model;
    }

    private static String getIdentifierFor(File alignment) {
        return alignment.getAbsolutePath();
    }

    private static Path getManipulationFilePath(File rootDir) {
        return rootDir.toPath().resolve(MANIPULATION_FILE_NAME);
    }

    /**
     * This is meant to be called from as part of a Gradle task that is executed for each project/subproject of the build
     * Is assumes that a task for various projects is never called in parallel
     * TODO verify that the non-parallel run assumption holds
     * 
     * @return a valid ManipulationModel.
     */
    public static ManipulationModel getCurrentManipulationModel(File rootDir) {
        return getManipulationModel(getManipulationFilePath(rootDir).toFile());
    }

    public static void addManipulationModel(File rootDir, ManipulationModel model) {
        cachedModels.put(getIdentifierFor(getManipulationFilePath(rootDir).toFile()), model);
    }

    /**
     * Write the model to disk - override any existing file that might exist
     * TODO verify comment of getCurrentManipulationModel since this method relies on the same assumption
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

}
