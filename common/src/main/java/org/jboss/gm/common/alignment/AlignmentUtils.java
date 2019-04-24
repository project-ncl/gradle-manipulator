package org.jboss.gm.common.alignment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

public final class AlignmentUtils {

    private static final String ALIGNMENT_FILE_NAME = "alignment.json";

    private AlignmentUtils() {
    }

    public static ManipulationModel getAlignmentModelAt(File path) {
        if (!path.isDirectory()) {
            throw new IllegalArgumentException("Path must be a directory. Was: " + path);
        }
        File alignment = new File(path, ALIGNMENT_FILE_NAME);
        return getAlignmentModel(alignment);
    }

    private static ManipulationModel getAlignmentModel(File alignment) {
        try {
            return SerializationUtils.getObjectMapper().readValue(alignment, ManipulationModel.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to deserialize " + ALIGNMENT_FILE_NAME, e);
        }
    }

    private static Path getAlignmentFilePath(File rootDir) {
        return rootDir.toPath().resolve(ALIGNMENT_FILE_NAME);
    }

    /**
     * This is meant to be called from as part of a Gradle task that is executed for each project/subproject of the build
     * Is assumes that a task for various projects is never called in parallel
     * TODO verify that the non-parallel run assumption holds
     * 
     * @return
     */
    public static ManipulationModel getCurrentAlignmentModel(File rootDir) {
        return getAlignmentModel(getAlignmentFilePath(rootDir).toFile());
    }

    /**
     * Write the model to disk - override any existing file that might exist
     * TODO verify comment of getCurrentAlignmentModel since this method relies on the same assumption
     */
    public static void writeUpdatedAlignmentModel(File rootDir, ManipulationModel updatedAlignmentModel) {
        final Path alignmentFilePath = AlignmentUtils.getAlignmentFilePath(rootDir);
        try {
            // first delete any existing file
            Files.delete(alignmentFilePath);
        } catch (IOException ignored) {
        }

        writeAlignmentModelToFile(alignmentFilePath, updatedAlignmentModel);
    }

    private static void writeAlignmentModelToFile(Path alignmentFilePath, ManipulationModel alignmentModel) {
        try {
            FileUtils.writeStringToFile(
                    alignmentFilePath.toFile(),
                    SerializationUtils.getObjectMapper().writeValueAsString(alignmentModel),
                    StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("Unable to write alignment.json in project root", e);
        }
    }

}
