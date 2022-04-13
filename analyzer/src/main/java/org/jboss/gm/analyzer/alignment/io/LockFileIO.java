package org.jboss.gm.analyzer.alignment.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.InvalidRefException;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.gradle.api.logging.Logger;
import org.jboss.gm.common.versioning.DynamicVersionParser;

/**
 * Utility class for lock file I/O.
 */
@UtilityClass
public class LockFileIO {

    private final String LOCKFILE_EXTENSION = ".lockfile";

    /**
     * Returns a set containing all project version refs from lock files in the given path.
     * This is not recursive.
     *
     * @param locksRootPath the path to the root for the lock files
     * @throws IOException if an error occurs
     * @return the set of all project version refs
     */
    public Set<ProjectVersionRef> allProjectVersionRefsFromLockfiles(File locksRootPath)
            throws IOException {
        final Set<ProjectVersionRef> result = new HashSet<>();
        getLockFiles(locksRootPath).forEach(f -> result.addAll(readProjectVersionRefLocksOfFile(f)));
        return result;
    }

    public Set<ProjectVersionRef> readProjectVersionRefLocksOfFile(File lockfile) {
        try {
            return FileUtils.readLines(lockfile, Charset.defaultCharset())
                    .stream()
                    .filter(l -> !l.startsWith("#"))
                    .map(l -> {
                        try {
                            return SimpleProjectVersionRef.parse(l.split("=")[0]);
                        } catch (InvalidRefException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new ManipulationUncheckedException("Unable to parse lockfile {}", lockfile, e);
        }
    }

    public List<File> getLockFiles(File locksRoot)
            throws IOException {
        if (!locksRoot.exists()) {
            return Collections.emptyList();
        }
        return Files.find(locksRoot.toPath(), 1,
                (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.getFileName().toString().endsWith(
                        (LOCKFILE_EXTENSION)))
                .map(Path::toFile).collect(
                        Collectors.toList());
    }

    public void updateLockfiles(Logger logger, File directory,
            Map<String, ProjectVersionRef> alignedDependencies) {
        List<File> locksFiles;

        try {
            locksFiles = getLockFiles(directory);
        } catch (IOException e) {
            throw new ManipulationUncheckedException(e);
        }

        for (File lockFile : locksFiles) {
            logger.debug("Examining lockfile {}", lockFile);

            try {
                List<String> lockFileLines = FileUtils.readLines(lockFile, Charset.defaultCharset());
                AtomicBoolean modified = new AtomicBoolean(false);

                alignedDependencies.forEach((key, value) -> {
                    ProjectVersionRef keyProjectVersionRef = SimpleProjectVersionRef.parse(key);
                    boolean dynamic = DynamicVersionParser.isDynamic(keyProjectVersionRef.getVersionString());
                    String matcher = key;
                    // Its possible alignedDependencies has a DynamicValue recorded as key but the lockfile has an
                    // explicit value. Therefore, we have to filter to find a partial match on group:artifact.
                    // One potential issue is that multiple same group:artifact with dynamic versions will
                    // all end up being replaced.
                    if (dynamic) {
                        matcher = keyProjectVersionRef.asProjectRef().toString();
                    }

                    for (int i = 0; i < lockFileLines.size(); i++) {
                        String line = lockFileLines.get(i);
                        if (line.contains(matcher)) {
                            logger.debug("Found lock file element '{}' to be replaced by {}", line, value);
                            line = line.replaceFirst(matcher +
                                    (dynamic ? ":([a-zA-Z0-9.]+)(=.*)*" : "(=.*)*"),
                                    value + (dynamic ? "$2" : "$1"));
                            lockFileLines.set(i, line);
                            modified.set(true);
                        }
                    }
                });
                if (modified.get()) {
                    FileUtils.writeLines(lockFile, lockFileLines);
                }
            } catch (IOException e) {
                throw new ManipulationUncheckedException(e);
            }
        }
    }
}
