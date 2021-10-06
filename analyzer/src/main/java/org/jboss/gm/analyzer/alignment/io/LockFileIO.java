package org.jboss.gm.analyzer.alignment.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
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
import org.gradle.api.Project;

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
     * @return the set of all project version refs
     */
    public Set<ProjectVersionRef> allProjectVersionRefsFromLockfiles(Path locksRootPath) {
        final Set<ProjectVersionRef> result = new HashSet<>();
        getAllLockfiles(locksRootPath).forEach(f -> result.addAll(readProjectVersionRefLocksOfFile(f)));
        return result;
    }

    private Set<ProjectVersionRef> readProjectVersionRefLocksOfFile(File lockfile) {
        try {
            return FileUtils.readLines(lockfile, Charset.defaultCharset())
                    .stream()
                    .filter(l -> !l.startsWith("#"))
                    .map(l -> {
                        try {
                            return SimpleProjectVersionRef.parse(l);
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
     */
    public void renameAllLockFiles(Path locksRootPath) {
        getAllLockfiles(locksRootPath).forEach(f -> {
            final Path path = f.toPath();
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".unused"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ManipulationUncheckedException("Unable to rename lockfile {}", f, e);
            }
        });
    }

    private List<File> getAllLockfiles(Path locksRootPath) {
        if (!locksRootPath.toFile().exists()) {
            return Collections.emptyList();
        }

        File[] lockfiles = locksRootPath.toFile()
                .listFiles((dir, filename) -> filename.endsWith(LOCKFILE_EXTENSION));
        if (lockfiles == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(lockfiles);
    }

    /**
     * Gets the lock file root path for the given project.
     *
     * @param project the project
     * @return the lock file root path
     */
    public Path getLocksRootPath(Project project) {
        return project.getProjectDir().toPath().resolve("gradle/dependency-locks");
    }
}
