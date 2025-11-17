package org.jboss.gm.analyzer.alignment.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import uk.org.webcompere.systemstubs.rules.SystemOutRule;

public class LockFileIOTest {

    private final Logger logger = GMLogger.getLogger(getClass());

    @Rule
    public LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule();

    @Test
    public void readNonExistingFileShouldReturnEmptySet()
            throws IOException {
        assertThat(LockFileIO.allProjectVersionRefsFromLockfiles(Paths.get("/lol").toFile())).isEmpty();
    }

    @Test
    public void readValidFileShouldReturnExpectedResults() throws URISyntaxException, IOException {
        copyToLockfilesRoot("compileClasspath.lockfile");
        copyToLockfilesRoot("runtimeClasspath.lockfile");

        final Set<ProjectVersionRef> result = LockFileIO.allProjectVersionRefsFromLockfiles(tempDir.getRoot());
        assertThat(result)
                .extracting("artifactId", "versionString")
                .containsOnly(
                        tuple("undertow-core", "2.0.21.Final"),
                        tuple("commons-lang3", "3.8"),
                        tuple("commons-lang3", "3.9"),
                        tuple("HdrHistogram", "2.1.10"),
                        tuple("guava", "25.1-android"),
                        tuple("xnio-nio", "3.3.8.Final"));
    }

    @Test
    public void testGetLockFile() throws URISyntaxException, IOException {
        copyToLockfilesRoot("compileClasspath.lockfile");
        copyToLockfilesRoot("runtimeClasspath.lockfile");

        List<File> locks = LockFileIO.getLockFiles(tempDir.getRoot());
        assertThat(locks.size()).isEqualTo(2);
    }

    @Test
    public void getLockFilesNotExisting() throws IOException {
        List<File> locks = LockFileIO.getLockFiles(new File(tempDir.getRoot(), "5801fd5c-7c58-4222-a761-f25b2f874704"));
        assertThat(locks.size()).isEqualTo(0);
    }

    private void copyToLockfilesRoot(String name) throws IOException, URISyntaxException {
        FileUtils.copyFile(
                Paths.get(LockFileIOTest.class.getClassLoader().getResource(name).toURI()).toFile(),
                tempDir.newFile(name));
    }

    @Test
    public void testUpdateLockFile() throws URISyntaxException, IOException {
        copyToLockfilesRoot("compileClasspath.lockfile");

        Map<String, ProjectVersionRef> map = new HashMap<>();
        map.put(
                "org.hdrhistogram:HdrHistogram:2.1.10",
                SimpleProjectVersionRef.parse("org.hdrhistogram:HdrHistogram:2.1.10.redhat-00001"));
        map.put(
                "org.apache.commons:commons-lang3:3.8",
                SimpleProjectVersionRef.parse("org.apache.commons:commons-lang3:3.8.redhat-00001"));

        LockFileIO.updateLockfiles(logger, tempDir.getRoot(), map);

        final Set<ProjectVersionRef> result = LockFileIO.allProjectVersionRefsFromLockfiles(tempDir.getRoot());
        assertThat(result)
                .extracting("artifactId", "versionString")
                .contains(
                        tuple("undertow-core", "2.0.21.Final"),
                        tuple("commons-lang3", "3.8.redhat-00001"),
                        tuple("HdrHistogram", "2.1.10.redhat-00001"),
                        tuple("guava", "25.1-android"));

        List<File> locks = LockFileIO.getLockFiles(tempDir.getRoot());
        assertThat(FileUtils.readLines(locks.get(0), Charset.defaultCharset())).anyMatch(
                f -> f.contains(
                        "2.1.10.redhat-00001=compileClasspath,runtimeClasspath"));

        assertThat(systemOutRule.getLinesNormalized()).contains(
                "Found lock file element 'org.apache.commons:commons-lang3:3"
                        + ".8' to be replaced by org.apache.commons:commons-lang3:3.8.redhat-00001");

    }
}
