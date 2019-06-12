package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;

final class LockfileIO {

    private static final String LOCKFILE_EXTENSION = ".lockfile";

    private LockfileIO() {
    }

    public static Set<ProjectVersionRef> allProjectVersionRefsFromLockfiles(Path locksRootPath) {
        final Set<ProjectVersionRef> result = new HashSet<>();
        getAllLockfiles(locksRootPath).forEach(f -> {
            result.addAll(readProjectVersionRefLocksOfFile(f));
        });
        return result;
    }

    private static Set<ProjectVersionRef> readProjectVersionRefLocksOfFile(File lockfile) {
        try {
            return FileUtils.readLines(lockfile, Charset.defaultCharset())
                    .stream()
                    .filter(l -> !l.startsWith("#"))
                    .map(l -> l.split(":"))
                    .filter(p -> p.length >= 3)
                    .map(p -> new SimpleProjectVersionRef(p[0], p[1], p[2]))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new ManipulationUncheckedException("Unable to parse lockfile " + lockfile, e);
        }
    }

    public static void renameAllLockFiles(Path locksRootPath) {
        getAllLockfiles(locksRootPath).forEach(f -> {
            final Path path = f.toPath();
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".unused"));
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
