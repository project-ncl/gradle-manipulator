package org.jboss.gm.analyzer.alignment.io;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class LockFileIOTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void readNonExistingFileShouldReturnEmptySet() {
        assertThat(LockFileIO.allProjectVersionRefsFromLockfiles(Paths.get("/lol"))).isEmpty();
    }

    @Test
    public void readValidFileShouldReturnExpectedResults() throws URISyntaxException, IOException {
        copyToLockfilesRoot("compileClasspath.lockfile");
        copyToLockfilesRoot("runtimeClasspath.lockfile");

        final Set<ProjectVersionRef> result = LockFileIO.allProjectVersionRefsFromLockfiles(tempDir.getRoot().toPath());
        assertThat(result)
                .extracting("artifactId", "versionString")
                .containsOnly(
                        tuple("undertow-core", "2.0.21.Final"),
                        tuple("commons-lang3", "3.8"),
                        tuple("HdrHistogram", "2.1.10"),
                        tuple("guava", "25.1-android"),
                        tuple("xnio-nio", "3.3.8.Final"));
    }

    @Test
    public void renameNonExistingFileShouldNotResultInAnError() {
        LockFileIO.renameAllLockFiles(Paths.get("/lol"));
    }

    @Test
    public void renameExistingFileShouldLeaveNoTraceOfLockfiles() throws URISyntaxException, IOException {
        copyToLockfilesRoot("compileClasspath.lockfile");
        copyToLockfilesRoot("runtimeClasspath.lockfile");

        LockFileIO.renameAllLockFiles(tempDir.getRoot().toPath());

        final File[] oldLockFiles = tempDir.getRoot()
                .listFiles((dir, filename) -> filename.endsWith(".lockfile"));
        assertThat(oldLockFiles).isNullOrEmpty();
    }

    private void copyToLockfilesRoot(String name) throws IOException, URISyntaxException {
        FileUtils.copyFile(
                Paths.get(LockFileIOTest.class.getClassLoader().getResource(name).toURI()).toFile(),
                tempDir.newFile(name));
    }
}
