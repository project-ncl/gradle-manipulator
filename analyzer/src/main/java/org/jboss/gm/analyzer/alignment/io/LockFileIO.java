package org.jboss.gm.analyzer.alignment.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.InvalidRefException;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

/**
 * Utility class for lock file I/O.
 */
@UtilityClass
public class LockFileIO {

    private final String LOCKFILE_EXTENSION = ".lockfile";

    /**
     * Returns a set containing all project version refs from lock files in the given path.
     *
     * @param locksRootPath the path to the root for the lock files
     * @throws IOException if an error occurs
     * @return the set of all project version refs
     */
    public Set<ProjectVersionRef> allProjectVersionRefsFromLockfiles(Path locksRootPath)
            throws IOException {
        final Set<ProjectVersionRef> result = new HashSet<>();
        getAllLockFiles(locksRootPath).forEach(f -> result.addAll(readProjectVersionRefLocksOfFile(f)));
        return result;
    }

    private Set<ProjectVersionRef> readProjectVersionRefLocksOfFile(File lockfile) {
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

    /**
     * Renames all lock files under the given root.
     *
     * @param locksRootPath the root path for the lock files
     * @throws IOException if an error occurs
     */
    public void renameAllLockFiles(Path locksRootPath)
            throws IOException {
        getAllLockFiles(locksRootPath).forEach(f -> {
            final Path path = f.toPath();
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".unused"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ManipulationUncheckedException("Unable to rename lockfile {}", f, e);
            }
        });
    }

    private List<File> getAllLockFiles(Path locksRootPath)
            throws IOException {
        if (!locksRootPath.toFile().exists()) {
            return Collections.emptyList();
        }
        return Files.find(locksRootPath, Integer.MAX_VALUE,
                (filePath, fileAttr) -> fileAttr.isRegularFile() && filePath.getFileName().toString().endsWith(
                        (LOCKFILE_EXTENSION)))
                .map(Path::toFile).collect(
                        Collectors.toList());
    }
}
