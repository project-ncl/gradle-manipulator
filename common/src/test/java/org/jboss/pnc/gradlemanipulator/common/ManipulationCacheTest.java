package org.jboss.pnc.gradlemanipulator.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationUncheckedException;
import org.junit.Test;

public class ManipulationCacheTest {

    @Test
    public void getProjectVersionRefs_throwsManipulationUncheckedException_whenDashSnapshotAndVersionSuffixSnapshotFalse() {
        ManipulationCache cache = new ManipulationCache(null);
        cache.addGAV(null, new SimpleProjectVersionRef("org.jboss.pnc", "test-art", "-SNAPSHOT"));

        assertThatExceptionOfType(ManipulationUncheckedException.class)
                .isThrownBy(() -> cache.getProjectVersionRefs(false))
                .withMessageContaining("has no base component after SNAPSHOT removal");
    }

    @Test
    public void getProjectVersionRefs_returnsOriginal_whenDashSnapshotAndVersionSuffixSnapshotTrue() {
        ManipulationCache cache = new ManipulationCache(null);
        cache.addGAV(null, new SimpleProjectVersionRef("org.jboss.pnc", "test-art", "-SNAPSHOT"));

        List<ProjectVersionRef> refs = cache.getProjectVersionRefs(true);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).getVersionString()).isEqualTo("-SNAPSHOT");
    }

    @Test
    public void getProjectVersionRefs_stripsSnapshot_whenVersionSuffixSnapshotFalse() {
        ManipulationCache cache = new ManipulationCache(null);
        cache.addGAV(null, new SimpleProjectVersionRef("org.jboss.pnc", "test-art", "1.0.0-SNAPSHOT"));

        List<ProjectVersionRef> refs = cache.getProjectVersionRefs(false);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).getVersionString()).isEqualTo("1.0.0");
    }
}
