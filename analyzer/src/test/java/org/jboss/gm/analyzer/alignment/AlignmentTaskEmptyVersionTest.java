package org.jboss.gm.analyzer.alignment;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.util.GradleVersion;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.gm.analyzer.alignment.io.SettingsFileIO;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.rules.LoggingRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(BMUnitRunner.class)
@BMUnitConfig(bmunitVerbose = true)
@BMRule(name = "handle-injectIntoNewInstance",
        targetClass = "org.gradle.api.internal.AbstractTask",
        targetMethod = "injectIntoNewInstance(ProjectInternal, TaskIdentity, Callable)",
        targetLocation = "AFTER INVOKE set",
        action = "RETURN null")
public class AlignmentTaskEmptyVersionTest {

    @Rule
    public final TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final LoggingRule loggingRule = new LoggingRule(LogLevel.INFO);

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    /**
     * We can't just create a new AlignmentTask as the base AbstractTask has checks to
     * prevent that. The <code>ThreadLocal&lt;TaskInfo&gt; NEXT_INSTANCE</code> is normally
     * set by AbstractTask.injectIntoNewInstance but that has a finally block that immediately
     * clears the value. The Byteman rule at the top bypasses the finally so we can create the dummy
     * task object.
     */
    @Before
    public void before()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final Class<? extends TaskInternal> taskType = Cast.uncheckedCast(DefaultTask.class);
        final ProjectInternal projectInternal = mock(ProjectInternal.class);
        when(projectInternal.getGradle()).thenReturn(mock(GradleInternal.class));
        when(projectInternal.getServices()).thenReturn(mock(ServiceRegistry.class));
        //noinspection UnstableApiUsage
        when(projectInternal.getObjects()).thenReturn(mock(ObjectFactory.class));

        if (GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0) {
            // In Gradle 8.2. this static method doesn't exist. However, we're only testing this under Gradle 8.0
            Method method = TaskIdentity.class.getMethod("create", String.class, Class.class, ProjectInternal.class);
            AbstractTask.injectIntoNewInstance(projectInternal,
                    (TaskIdentity) method.invoke(null, "DummyIdentity", taskType,
                            projectInternal),
                    null);
        }
    }

    @Test
    public void testProject() throws Exception {
        // XXX: Caused by: org.gradle.api.GradleException: Dependencies can not be declared against the `default` configuration.
        assumeTrue(GradleVersion.current().compareTo(GradleVersion.version("8.0")) < 0);

        final File simpleProjectRoot = tempDir.newFolder("simple-project");

        System.setProperty("ignoreUnresolvableDependencies", "true");

        Project p = ProjectBuilder.builder().withProjectDir(simpleProjectRoot).build();

        p.apply(configuration -> configuration.plugin("base"));

        p.getRepositories().mavenCentral();
        p.getDependencies().add("default", "org.apache.commons:commons-configuration2:2.4");
        p.getDependencies().add("default", "org.apache.commons:dummy-artifact");

        AlignmentTask at = new AlignmentTask();
        Configuration config = ConfigFactory.create(Configuration.class);

        // As getDependencies is private, use reflection to modify the access control.
        Method m = at.getClass().getDeclaredMethod("getDependencies", Project.class, Configuration.class, Set.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Dependency, ProjectVersionRef> result = (Map<Dependency, ProjectVersionRef>) m.invoke(at,
                new Object[] { p, config, new HashSet<ProjectVersionRef>() });
        Collection<ProjectVersionRef> allDependencies = result.values();

        assertEquals(1, allDependencies.size());
        assertEquals("org.apache.commons:commons-configuration2:2.4", allDependencies.toArray()[0].toString());
    }

    @Test
    public void verifyPluginLog() {
        new AlignmentPlugin();
        assertTrue(systemOutRule.getLog().contains("Running Gradle Alignment Plugin"));
    }

    @Test
    public void testParseScm() throws Exception {
        Map<String, String> formats = new HashMap<>();

        formats.put("  url = git@github.com:rnc/pom-manipulation-ext.git", "pom-manipulation-ext");
        formats.put(" url = git+ssh://code.foo.com.com/apache/kafka", "kafka");
        formats.put("url = git+ssh://code.foo.com/apache/kafka.git", "kafka");
        formats.put("url = https://github.com/quarkusio/quarkus/", "quarkus");
        formats.put("url = michalszynkiewicz/test.foo", "test.foo");
        formats.put("url = michalszynkiewicz/_test.foo", "_test.foo");

        Method m = SettingsFileIO.class.getDeclaredMethod("extractProjectNameFromScmUrl", File.class);
        m.setAccessible(true);

        File gitFolder = tempDir.newFolder(".git");

        for (String line : formats.keySet()) {
            File tempFile = new File(gitFolder, "config");
            FileUtils.writeStringToFile(tempFile, line, Charset.defaultCharset());

            String result = (String) m.invoke(null, new Object[] { tempDir.getRoot() });

            assertEquals(result, formats.get(line));
        }
        assertTrue(systemOutRule.getLog().contains("Examining git config content"));
    }
}
