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

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.InvalidRefException;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

public final class LockFileIO {

    private static final String LOCKFILE_EXTENSION = ".lockfile";

    private LockFileIO() {
    }

    public static Set<ProjectVersionRef> allProjectVersionRefsFromLockfiles(Path locksRootPath) {
        final Set<ProjectVersionRef> result = new HashSet<>();
        getAllLockfiles(locksRootPath).forEach(f -> result.addAll(readProjectVersionRefLocksOfFile(f)));
        return result;
    }

    private static Set<ProjectVersionRef> readProjectVersionRefLocksOfFile(File lockfile) {
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
            throw new ManipulationUncheckedException("Unable to parse lockfile " + lockfile, e);
        }
    }

    public static void renameAllLockFiles(Path locksRootPath) {
        getAllLockfiles(locksRootPath).forEach(f -> {
            final Path path = f.toPath();
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".unused"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ManipulationUncheckedException("Unable to rename lockfile " + f, e);
            }
        });
    }

    private static List<File> getAllLockfiles(Path locksRootPath) {
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
}
