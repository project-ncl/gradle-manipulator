package org.jboss.gm.common.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.SerializationUtils;

public final class ManipulationIO {
    private static final String MANIPULATION_FILE_NAME = "manipulation.json";

    private ManipulationIO() {
    }

    /**
     * This is meant to be called from as part of a Gradle task that is executed for each project/subproject of the build
     * Is assumes that a task for various projects is never called in parallel
     *
     * @return a valid ManipulationModel.
     */
    public static ManipulationModel readManipulationModel(File rootDir) {
        try {
            return SerializationUtils.getObjectMapper().readValue(getManipulationFilePath(rootDir).toFile(),
                    ManipulationModel.class);

        } catch (IOException e) {
            throw new ManipulationUncheckedException("Unable to deserialize " + MANIPULATION_FILE_NAME, e);
        }
    }

    /**
     * Write the model to disk - override any existing file that might exist
     */
    public static void writeManipulationModel(File rootDir, ManipulationModel updatedManipulationModel)
            throws ManipulationException {
        final Path manipulationFilePath = ManipulationIO.getManipulationFilePath(rootDir);
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

    private static Path getManipulationFilePath(File rootDir) {
        return rootDir.toPath().resolve(MANIPULATION_FILE_NAME);
    }
}
