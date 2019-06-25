package org.jboss.gm.common.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.SerializationUtils;

public final class ManipulationIO {
    public static final String MANIPULATION_FILE_NAME = "manipulation.json";

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
            return SerializationUtils.getObjectMapper().readValue(new File(rootDir, MANIPULATION_FILE_NAME),
                    ManipulationModel.class);

        } catch (IOException e) {
            throw new ManipulationUncheckedException("Unable to deserialize " + MANIPULATION_FILE_NAME, e);
        }
    }

    /**
     * Write the model to disk - override any existing file that might exist
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void writeManipulationModel(File rootDir, ManipulationModel updatedManipulationModel)
            throws ManipulationException {
        final File manipulationFilePath = new File(rootDir, MANIPULATION_FILE_NAME);

        // first delete any existing file
        manipulationFilePath.delete();

        try {
            FileUtils.writeStringToFile(
                    manipulationFilePath,
                    SerializationUtils.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                            updatedManipulationModel),
                    StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new ManipulationException("Unable to write manipulation.json in project root", e);
        }
    }
}
