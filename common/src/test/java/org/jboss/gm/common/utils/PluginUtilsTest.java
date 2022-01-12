package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.logging.LogLevel;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginUtilsTest {
    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.DEBUG);

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Test(expected = ManipulationException.class)
    public void testInvalidPlugin()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "# empty file", Charset.defaultCharset());

        PluginUtils.pluginRemoval(logger, target.getParentFile(), Collections.singleton("gradle-enterprise-2"));
    }

    @Test
    public void testNoRemoval()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "# empty file\n", Charset.defaultCharset());

        PluginUtils.pluginRemoval(logger, target.getParentFile(), Collections.singleton("gradle-enterprise"));

        assertFalse(systemOutRule.getLog()
                .contains("Looking to remove gradle-enterprise with configuration block of gradleEnterprise"));
        assertFalse(systemOutRule.getLog().contains("Removed instances of plugin"));
    }

    @Test
    public void testNoRemoval2()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "# empty file\n", Charset.defaultCharset());

        PluginUtils.pluginRemoval(logger, target.getParentFile(), null);

        assertFalse(systemOutRule.getLog()
                .contains("Looking to remove gradle-enterprise with configuration block of gradleEnterprise"));
        assertFalse(systemOutRule.getLog().contains("Removed instances of plugin"));
    }

    @Test
    public void testRemovalWithUnbalancedBrackets()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle.kts");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "plugins {\n" + "    `gradle-enterprise`\n"
                        + "    id(\"com.github.burrunan.s3-build-cache\")\n"
                        + "}\n" + "\n"
                        + "// This is the name of a current project\n"
                        + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                        + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n"
                        + "    \"bom\",\n" + "    \"benchmarks\",\n"
                        + "    \"postgresql\"\n" + ")\n" + "\n"
                        + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n"
                        + "\n"
                        + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                        + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                        + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n"
                        + "\n" + "fun property(name: String) =\n"
                        + "    when (extra.has(name)) {\n"
                        + "        true -> extra.get(name) as? String\n"
                        + "        else -> null\n" + "    }\n" + "\n"
                        + "val isCiServer = System.getenv().containsKey(\"CI\")\n"
                        + "gradleEnterprise {\n" + "        buildScan {\n"
                        + "        // This { is wrong."
                        + "        termsOfServiceAgree = \"yes\"\n"
                        + "        tag(\"CI\")\n" + "        }\n" + "    }\n",
                Charset.defaultCharset());

        PluginUtils.pluginRemoval(logger, target.getParentFile(), Collections.singleton("gradle-enterprise"));
        assertTrue(systemOutRule.getLog()
                .contains("Removed instances of plugin gradle-enterprise with configuration block of gradleEnterprise"));
        assertTrue(systemOutRule.getLog().contains("Removed instances of plugin"));

        String result = "plugins {\n" + "    id(\"com.github.burrunan.s3-build-cache\")\n" + "}\n" + "\n"
                + "// This is the name of a current project\n"
                + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n" + "    \"bom\",\n" + "    \"benchmarks\",\n"
                + "    \"postgresql\"\n" + ")\n" + "\n" + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n" + "\n"
                + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n" + "\n"
                + "fun property(name: String) =\n" + "    when (extra.has(name)) {\n"
                + "        true -> extra.get(name) as? String\n" + "        else -> null\n" + "    }\n" + "\n"
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n";
        assertEquals(result, FileUtils.readFileToString(target, Charset.defaultCharset()));
    }

    @Test
    public void testRemoval1()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle.kts");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "plugins {\n" + "    `gradle-enterprise`\n"
                        + "    id(\"com.github.burrunan.s3-build-cache\")\n"
                        + "}\n" + "\n"
                        + "// This is the name of a current project\n"
                        + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                        + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n"
                        + "    \"bom\",\n" + "    \"benchmarks\",\n"
                        + "    \"postgresql\"\n" + ")\n" + "\n"
                        + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n"
                        + "\n"
                        + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                        + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                        + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n"
                        + "\n" + "fun property(name: String) =\n"
                        + "    when (extra.has(name)) {\n"
                        + "        true -> extra.get(name) as? String\n"
                        + "        else -> null\n" + "    }\n" + "\n"
                        + "val isCiServer = System.getenv().containsKey(\"CI\")\n"
                        + "\n" + "if (isCiServer) {\n"
                        + "    gradleEnterprise {\n" + "        buildScan {\n"
                        + "            termsOfServiceUrl = \"https://gradle.com/terms-of-service\"\n"
                        + "            termsOfServiceAgree = \"yes\"\n"
                        + "            tag(\"CI\")\n" + "        }\n" + "    }\n"
                        + "}\n",
                Charset.defaultCharset());

        PluginUtils.pluginRemoval(logger, target.getParentFile(), Collections.singleton("gradle-enterprise"));

        assertTrue(systemOutRule.getLog()
                .contains("Removed instances of plugin gradle-enterprise with configuration block of gradleEnterprise"));
        assertTrue(systemOutRule.getLog().contains("Removed instances of plugin"));

        String result = "plugins {\n" + "    id(\"com.github.burrunan.s3-build-cache\")\n" + "}\n" + "\n"
                + "// This is the name of a current project\n"
                + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n" + "    \"bom\",\n" + "    \"benchmarks\",\n"
                + "    \"postgresql\"\n" + ")\n" + "\n" + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n" + "\n"
                + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n" + "\n"
                + "fun property(name: String) =\n" + "    when (extra.has(name)) {\n"
                + "        true -> extra.get(name) as? String\n" + "        else -> null\n" + "    }\n" + "\n"
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n" + "\n" + "if (isCiServer) {\n" + "}\n";
        assertEquals(result, FileUtils.readFileToString(target, Charset.defaultCharset()));
    }

    @Test
    public void testRemoval2()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target, "plugins {\n" + "    `gradle-enterprise`\n"
                + "    id(\"com.github.burrunan.s3-build-cache\")\n" + "}\n"
                + "\n"
                + "// This is the name of a current project\n"
                + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n"
                + "    \"bom\",\n" + "    \"benchmarks\",\n"
                + "    \"postgresql\"\n" + ")\n" + "\n" + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n"
                + "\n"
                + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n"
                + "\n"
                + "fun property(name: String) =\n" + "    when (extra.has(name)) {\n"
                + "        true -> extra.get(name) as? String\n" + "        else -> null\n"
                + "    }\n" + "\n"
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n"
                + "\n" + "if (isCiServer) {\n"
                + "    gradleEnterprise {\n" + "        buildScan {\n"
                + "            termsOfServiceUrl = \"https://gradle.com/terms-of-service\"\n"
                + "            termsOfServiceAgree = \"yes\"\n" + "            tag(\"CI\")\n"
                + "        }\n" + "    }\n"
                + "}\n" + "buildCache {\n" + "    local {\n"
                + "        // Local build cache is dangerous as it might produce inconsistent results\n"
                + "        // in case developer modifies files while the build is running\n"
                + "        enabled = false\n"
                + "    }\n" + "    remote(com.github.burrunan.s3cache.AwsS3BuildCache) {\n"
                + "        region = 'eu-west-1'\n" + "        bucket = 'your-bucket'\n"
                + "        prefix = 'cache/'\n"
                + "        push = isCiServer\n"
                + "        // Credentials will be taken from  S3_BUILD_CACHE_... environment variables\n"
                + "        // anonymous access will be used if environment variables are missing\n"
                + "    }\n"
                + "}\n"
                + "if (true) { }\n",
                Charset.defaultCharset());

        HashSet<String> plugins = new HashSet<>();
        plugins.add("com.github.burrunan.s3-build-cache");
        plugins.add("gradle-enterprise");
        PluginUtils.pluginRemoval(logger, target.getParentFile(), plugins);

        assertTrue(systemOutRule.getLog()
                .contains(
                        "Removed instances of plugin com.github.burrunan.s3-build-cache with configuration block of buildCache"));
        assertTrue(systemOutRule.getLog().contains("Removed instances of plugin"));

        String result = "plugins {\n" + "}\n" + "\n" + "// This is the name of a current project\n"
                + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n" + "    \"bom\",\n" + "    \"benchmarks\",\n"
                + "    \"postgresql\"\n" + ")\n" + "\n" + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n" + "\n"
                + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n" + "\n"
                + "fun property(name: String) =\n" + "    when (extra.has(name)) {\n"
                + "        true -> extra.get(name) as? String\n" + "        else -> null\n" + "    }\n" + "\n"
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n" + "\n" + "if (isCiServer) {\n" + "}\n"
                + "if (true) { }\n";
        assertEquals(result, FileUtils.readFileToString(target, Charset.defaultCharset()));
    }

    @Test
    public void testRemoval4()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target, "plugins {\n" + "    `gradle-enterprise`\n"
                + "    id(\"com.github.burrunan.s3-build-cache\")\n" + "}\n"
                + "\n"
                + "// This is the name of a current project\n"
                + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n"
                + "    \"bom\",\n" + "    \"benchmarks\",\n"
                + "    \"postgresql\"\n" + ")\n" + "\n" + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n"
                + "\n"
                + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n"
                + "\n"
                + "fun property(name: String) =\n" + "    when (extra.has(name)) {\n"
                + "        true -> extra.get(name) as? String\n" + "        else -> null\n"
                + "    }\n" + "\n"
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n"
                + "\n" + "if (isCiServer) {\n"
                + "    gradleEnterprise {\n" + "        buildScan {\n"
                + "            termsOfServiceUrl = \"https://gradle.com/terms-of-service\"\n"
                + "            termsOfServiceAgree = \"yes\"\n" + "            tag(\"CI\")\n"
                + "        }\n" + "    }\n"
                + "}\n" + "buildCache {\n" + "    local {\n"
                + "        // Local build cache is dangerous as it might produce inconsistent results\n"
                + "        // in case developer modifies files while the build is running\n"
                + "        enabled = false\n"
                + "    }\n" + "    remote(com.github.burrunan.s3cache.AwsS3BuildCache) {\n"
                + "        region = 'eu-west-1'\n" + "        bucket = 'your-bucket'\n"
                + "        prefix = 'cache/'\n"
                + "        push = isCiServer\n"
                + "        // Credentials will be taken from  S3_BUILD_CACHE_... environment variables\n"
                + "        // anonymous access will be used if environment variables are missing\n"
                + "    }\n"
                + "}\nif (true) { }\n",
                Charset.defaultCharset());

        // Avoid singleton as the set is manipulated within the method
        PluginUtils.pluginRemoval(logger, target.getParentFile(), new HashSet<>(Collections.singleton("ALL")));

        assertTrue(systemOutRule.getLog()
                .contains("Removed instances of plugin gradle-enterprise with configuration block of gradleEnterprise from"));
        assertTrue(systemOutRule.getLog().contains("Removed instances of plugin"));

        String result = "plugins {\n" + "}\n" + "\n" + "// This is the name of a current project\n"
                + "// Note: it cannot be inferred from the directory name as developer might clone pgjdbc to pgjdbc_tmp (or whatever) folder\n"
                + "rootProject.name = \"pgjdbc\"\n" + "\n" + "include(\n" + "    \"bom\",\n" + "    \"benchmarks\",\n"
                + "    \"postgresql\"\n" + ")\n" + "\n" + "project(\":postgresql\").projectDir = file(\"pgjdbc\")\n" + "\n"
                + "// Gradle inherits Ant \"default excludes\", however we do want to archive those files\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitattributes\")\n"
                + "org.apache.tools.ant.DirectoryScanner.removeDefaultExclude(\"**/.gitignore\")\n" + "\n"
                + "fun property(name: String) =\n" + "    when (extra.has(name)) {\n"
                + "        true -> extra.get(name) as? String\n" + "        else -> null\n" + "    }\n" + "\n"
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n" + "\n" + "if (isCiServer) {\n" + "}\n"
                + "if (true) { }\n";
        assertEquals(result, FileUtils.readFileToString(target, Charset.defaultCharset()));
    }

    @Test
    public void testRemoval5()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "import org.gradle.api.tasks.testing.logging.TestExceptionFormat\n"
                        + "import org.gradle.api.tasks.testing.logging.TestLogEvent\n" + "\n" + "plugins {\n"
                        + "    id \"org.jruyi.thrift\" version \"0.4.0\"\n" + "    id \"jacoco\"\n"
                        + "    id \"com.github.hierynomus.license\" version \"0.15.0\"\n"
                        + "    id \"com.github.johnrengelman.shadow\" version \"5.0.0\"\n"
                        + "    id \"net.ltgt.errorprone\" version \"0.0.14\"\n"
                        + "    id 'ru.vyarus.animalsniffer' version '1.5.0'\n" + "    id 'java-library'\n"
                        + "    id 'maven-publish'\n" + "    id 'signing'\n"
                        + "    id 'io.codearte.nexus-staging' version '0.20.0'\n"
                        + "    id \"de.marcphilipp.nexus-publish\" version \"0.2.0\" apply false\n"
                        + "    id 'com.github.ben-manes.versions' version '0.21.0'\n"
                        + "    id 'net.researchgate.release' version '2.6.0'\n" + "}\n",
                Charset.defaultCharset());

        File subfolder = folder.newFolder();
        File subtarget = new File(subfolder, "publish.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(subtarget, "signing {\n" + "    if (isReleaseVersion) {\n"
                + "        sign publishing.publications.mavenJava\n" + "    }\n"
                + "}\n", Charset.defaultCharset());

        // Avoid singleton as the set is manipulated within the method
        PluginUtils.pluginRemoval(logger, target.getParentFile(), new HashSet<>(Collections.singleton("ALL")));

        assertTrue(systemOutRule.getLog()
                .contains("Removed instances of plugin 'signing' with configuration block of signing from"));

        assertFalse(FileUtils.readFileToString(target, Charset.defaultCharset()).contains("com.github.ben-manes.versions"));
        assertTrue(FileUtils.readFileToString(subtarget, Charset.defaultCharset()).trim().isEmpty());
    }
}
