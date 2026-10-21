package org.jboss.pnc.gradlemanipulator.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RepositoryUtilsTest {

    private static final String PLUGIN_PORTAL = "https://plugins.gradle.org/m2";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File script(String content) throws IOException {
        File file = folder.newFile();
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private Set<String> urls(String content) throws IOException {
        return RepositoryUtils.collectInitScriptRepositoryUrls(Collections.singletonList(script(content)));
    }

    @Test
    public void noScriptsGivesEmptySets() {
        assertTrue(RepositoryUtils.collectInitScriptRepositoryUrls(Collections.emptyList()).isEmpty());
        assertTrue(RepositoryUtils.collectInitScriptRepositoryNames(Collections.emptyList()).isEmpty());
    }

    @Test
    public void explicitUrlsAndNamesAreCollected() throws IOException {
        File f = script("maven {\n name = \"mirror\"\n url = \"https://example.com/m2/\"\n}\n");

        assertEquals(
                Collections.singleton("https://example.com/m2"),
                RepositoryUtils.collectInitScriptRepositoryUrls(Collections.singletonList(f)));
        assertEquals(
                Collections.singleton("mirror"),
                RepositoryUtils.collectInitScriptRepositoryNames(Collections.singletonList(f)));
    }

    @Test
    public void gradlePluginPortalShortNameIsExcluded() throws IOException {
        assertEquals(Collections.singleton(PLUGIN_PORTAL), urls("repositories.gradlePluginPortal()"));
        assertEquals(Collections.singleton(PLUGIN_PORTAL), urls("repositories { gradlePluginPortal { } }"));
    }

    @Test
    public void gradlePluginPortalExplicitUrlIsExcluded() throws IOException {
        assertEquals(
                Collections.singleton(PLUGIN_PORTAL),
                urls("maven { url = \"https://plugins.gradle.org/m2/\" }"));
        assertEquals(Collections.singleton(PLUGIN_PORTAL), urls("maven { url 'https://plugins.gradle.org/m2' }"));
    }

    @Test
    public void mavenCentralAndGoogleShortNamesAreNotExcluded() throws IOException {
        assertTrue(urls("repositories.mavenCentral()\nrepositories { google() }").isEmpty());
    }

    @Test
    public void mavenCentralAndGoogleExplicitUrlsAreNotExcluded() throws IOException {
        assertTrue(urls("maven { url = \"https://repo.maven.apache.org/maven2/\" }").isEmpty());
        assertTrue(urls("maven { url 'https://repo.maven.apache.org/maven2' }").isEmpty());
        assertTrue(urls("maven { url = \"https://dl.google.com/dl/android/maven2/\" }").isEmpty());
    }

    @Test
    public void unrelatedMethodNamesAreNotMatched() throws IOException {
        assertTrue(urls("def gradlePluginPortalMirror = 1\ndef notgradlePluginPortal() {}").isEmpty());
    }
}
