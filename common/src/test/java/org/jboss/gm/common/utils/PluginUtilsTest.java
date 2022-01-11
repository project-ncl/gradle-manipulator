package org.jboss.gm.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

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

        String[] plugins = { "gradle-enterprise-2" };
        PluginUtils.pluginRemoval( logger, target.getParentFile(), plugins );
    }

    @Test
    public void testNoRemoval()
            throws IOException, ManipulationException {

        File target = folder.newFile("build.gradle");
        org.apache.commons.io.FileUtils.writeStringToFile(target,
                "# empty file", Charset.defaultCharset());

        String[] plugins = { "gradle-enterprise" };
        PluginUtils.pluginRemoval( logger, target.getParentFile(), plugins );

        assertTrue(systemOutRule.getLog()
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

        String[] plugins = { "gradle-enterprise" };
        PluginUtils.pluginRemoval( logger, target.getParentFile(), plugins );
        assertTrue(systemOutRule.getLog()
                .contains("Looking to remove gradle-enterprise with configuration block of gradleEnterprise"));
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
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n\n";
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

        String[] plugins = { "gradle-enterprise" };
        PluginUtils.pluginRemoval( logger, target.getParentFile(), plugins );

        assertTrue(systemOutRule.getLog()
                .contains("Looking to remove gradle-enterprise with configuration block of gradleEnterprise"));
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
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n" + "\n" + "if (isCiServer) {\n    \n" + "}\n";
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
                + "} if (true) { }\n",
                Charset.defaultCharset());

        String[] plugins = { "com.github.burrunan.s3-build-cache", "gradle-enterprise" };
        PluginUtils.pluginRemoval( logger, target.getParentFile(), plugins );

        assertTrue(systemOutRule.getLog()
                .contains("Looking to remove gradle-enterprise with configuration block of gradleEnterprise"));
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
                + "val isCiServer = System.getenv().containsKey(\"CI\")\n" + "\n" + "if (isCiServer) {\n" + "    \n" + "}\n"
                + " if (true) { }\n";
        assertEquals(result, FileUtils.readFileToString(target, Charset.defaultCharset()));
    }
}
