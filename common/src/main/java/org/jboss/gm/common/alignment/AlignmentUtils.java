package org.jboss.gm.common.alignment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

public final class AlignmentUtils {

    private static final String ALIGNMENT_FILE_NAME = "alignment.json";

    private AlignmentUtils() {
    }

    public static Path getAlignmentFilePath(Project project) {
        return project.getRootDir().toPath().resolve(ALIGNMENT_FILE_NAME);
    }

    /**
     * This is meant to be called from as part of a Gradle task that is executed for each project/subproject of the build
     * Is assumes that a task for various projects is never called in parallel
     * TODO verify that the non-parallel run assumption holds
     */
    public static AlignmentModel getCurrentAlignmentModel(Project project) {
        try {
            return SerializationUtils.getObjectMapper().readValue(getAlignmentFilePath(project).toFile(), AlignmentModel.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to deserialize " + ALIGNMENT_FILE_NAME, e);
        }
    }

    /**
     * Write the model to disk - override any existing file that might exist
     * TODO verify comment of getCurrentAlignmentModel since this method relies on the same assumption
     */
    public static void writeUpdatedAlignmentModel(Project project, AlignmentModel updatedAlignmentModel) {
        final Path alignmentFilePath = AlignmentUtils.getAlignmentFilePath(project);
        try {
            // first delete any existing file
            Files.delete(alignmentFilePath);
        } catch (IOException ignored) {
        }

        writeAlignmentModelToFile(alignmentFilePath, updatedAlignmentModel);
    }

    private static void writeAlignmentModelToFile(Path alignmentFilePath, AlignmentModel alignmentModel) {
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
