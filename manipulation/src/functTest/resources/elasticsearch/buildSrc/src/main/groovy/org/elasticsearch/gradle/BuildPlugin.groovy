/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import org.apache.commons.io.IOUtils
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.internal.jvm.Jvm
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.util.GradleVersion

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.stream.Stream

/**
 * Encapsulates build configuration for elasticsearch projects.
 */
class BuildPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (project.pluginManager.hasPlugin('elasticsearch.standalone-rest-test')) {
              throw new InvalidUserDataException('elasticsearch.standalone-test, '
                + 'elasticsearch.standalone-rest-test, and elasticsearch.build '
                + 'are mutually exclusive')
        }
        String minimumGradleVersion = null
        InputStream is = getClass().getResourceAsStream("/minimumGradleVersion")
        try { minimumGradleVersion = IOUtils.toString(is, StandardCharsets.UTF_8.toString()) } finally { is.close() }
        if (GradleVersion.current() < GradleVersion.version(minimumGradleVersion.trim())) {
            throw new GradleException(
                    "Gradle ${minimumGradleVersion}+ is required to use elasticsearch.build plugin"
            )
        }
        project.pluginManager.apply('java')
        configureConfigurations(project)
        configureJars(project) // jar config must be added before info broker
        // these plugins add lots of info to our jars
        project.pluginManager.apply('nebula.info-broker')
        project.pluginManager.apply('nebula.info-basic')
        project.pluginManager.apply('nebula.info-java')
        project.pluginManager.apply('nebula.info-scm')
        project.pluginManager.apply('nebula.info-jar')

        // project.getTasks().create("buildResources", ExportElasticsearchBuildResourcesTask)

        globalBuildInfo(project)
        configureRepositories(project)
        project.ext.versions = VersionProperties.versions
        configureSourceSets(project)
        configureCompile(project)
        configureJavadoc(project)
        configureSourcesJar(project)
        configurePomGeneration(project)

        applyCommonTestConfig(project)
        configureTest(project)
        configurePrecommit(project)
        configureDependenciesInfo(project)
    }



    /** Performs checks on the build environment and prints information about the build environment. */
    static void globalBuildInfo(Project project) {
        if (project.rootProject.ext.has('buildChecksDone') == false) {
            JavaVersion minimumRuntimeVersion = JavaVersion.toVersion(
                    BuildPlugin.class.getClassLoader().getResourceAsStream("minimumRuntimeVersion").text.trim()
            )
            JavaVersion minimumCompilerVersion = JavaVersion.toVersion(
                    BuildPlugin.class.getClassLoader().getResourceAsStream("minimumCompilerVersion").text.trim()
            )
            String compilerJavaHome = findCompilerJavaHome()
            String runtimeJavaHome = findRuntimeJavaHome(compilerJavaHome)
            File gradleJavaHome = Jvm.current().javaHome

            final Map<Integer, String> javaVersions = [:]
            for (int version = 8; version <= Integer.parseInt(minimumCompilerVersion.majorVersion); version++) {
                if(System.getenv(getJavaHomeEnvVarName(version.toString())) != null) {
                    javaVersions.put(version, findJavaHome(version.toString()));
                }
            }

            String javaVendorVersion = System.getProperty('java.vendor.version', System.getProperty('java.vendor'))
            String gradleJavaVersion = System.getProperty('java.version')
            String gradleJavaVersionDetails = "${javaVendorVersion} ${gradleJavaVersion}" +
                " [${System.getProperty('java.vm.name')} ${System.getProperty('java.vm.version')}]"

            String compilerJavaVersionDetails = gradleJavaVersionDetails
            JavaVersion compilerJavaVersionEnum = JavaVersion.current()
            if (new File(compilerJavaHome).canonicalPath != gradleJavaHome.canonicalPath) {
                compilerJavaVersionDetails = findJavaVersionDetails(project, compilerJavaHome)
                compilerJavaVersionEnum = JavaVersion.toVersion(findJavaSpecificationVersion(project, compilerJavaHome))
            }

            String runtimeJavaVersionDetails = gradleJavaVersionDetails
            JavaVersion runtimeJavaVersionEnum = JavaVersion.current()
            if (new File(runtimeJavaHome).canonicalPath != gradleJavaHome.canonicalPath) {
                runtimeJavaVersionDetails = findJavaVersionDetails(project, runtimeJavaHome)
                runtimeJavaVersionEnum = JavaVersion.toVersion(findJavaSpecificationVersion(project, runtimeJavaHome))
            }

            String inFipsJvmScript = 'print(java.security.Security.getProviders()[0].name.toLowerCase().contains("fips"));'
            boolean inFipsJvm = Boolean.parseBoolean(runJavaAsScript(project, runtimeJavaHome, inFipsJvmScript))

            // Build debugging info
            println '======================================='
            println 'Elasticsearch Build Hamster says Hello!'
            println "  Gradle Version        : ${project.gradle.gradleVersion}"
            println "  OS Info               : ${System.getProperty('os.name')} ${System.getProperty('os.version')} (${System.getProperty('os.arch')})"
            if (gradleJavaVersionDetails != compilerJavaVersionDetails || gradleJavaVersionDetails != runtimeJavaVersionDetails) {
                println "  Compiler JDK Version  : ${compilerJavaVersionEnum} (${compilerJavaVersionDetails})"
                println "  Compiler java.home    : ${compilerJavaHome}"
                println "  Runtime JDK Version   : ${runtimeJavaVersionEnum} (${runtimeJavaVersionDetails})"
                println "  Runtime java.home     : ${runtimeJavaHome}"
                println "  Gradle JDK Version    : ${JavaVersion.toVersion(gradleJavaVersion)} (${gradleJavaVersionDetails})"
                println "  Gradle java.home      : ${gradleJavaHome}"
            } else {
                println "  JDK Version           : ${JavaVersion.toVersion(gradleJavaVersion)} (${gradleJavaVersionDetails})"
                println "  JAVA_HOME             : ${gradleJavaHome}"
            }
            println '======================================='

            for (final Map.Entry<Integer, String> javaVersionEntry : javaVersions.entrySet()) {
                final String javaHome = javaVersionEntry.getValue()
                if (javaHome == null) {
                    continue
                }
                JavaVersion javaVersionEnum = JavaVersion.toVersion(findJavaSpecificationVersion(project, javaHome))
                final JavaVersion expectedJavaVersionEnum
                final int version = javaVersionEntry.getKey()
                if (version < 9) {
                    expectedJavaVersionEnum = JavaVersion.toVersion("1." + version)
                } else {
                    expectedJavaVersionEnum = JavaVersion.toVersion(Integer.toString(version))
                }
                if (javaVersionEnum != expectedJavaVersionEnum) {
                    final String message =
                            "the environment variable JAVA" + version + "_HOME must be set to a JDK installation directory for Java" +
                                    " ${expectedJavaVersionEnum} but is [${javaHome}] corresponding to [${javaVersionEnum}]"
                    throw new GradleException(message)
                }
            }

            project.rootProject.ext.compilerJavaHome = compilerJavaHome
            project.rootProject.ext.runtimeJavaHome = runtimeJavaHome
            project.rootProject.ext.compilerJavaVersion = compilerJavaVersionEnum
            project.rootProject.ext.runtimeJavaVersion = runtimeJavaVersionEnum
            project.rootProject.ext.javaVersions = javaVersions
            project.rootProject.ext.buildChecksDone = true
            project.rootProject.ext.minimumCompilerVersion = minimumCompilerVersion
            project.rootProject.ext.minimumRuntimeVersion = minimumRuntimeVersion
            project.rootProject.ext.inFipsJvm = inFipsJvm
            project.rootProject.ext.gradleJavaVersion = JavaVersion.toVersion(gradleJavaVersion)
            project.rootProject.ext.java9Home = "${-> findJavaHome("9")}"
            project.rootProject.ext.defaultParallel = findDefaultParallel(project.rootProject)
            project.rootProject.ext.gitRevision = gitRevision(project)
            project.rootProject.ext.buildDate = ZonedDateTime.now(ZoneOffset.UTC);
        }

        project.targetCompatibility = project.rootProject.ext.minimumRuntimeVersion
        project.sourceCompatibility = project.rootProject.ext.minimumRuntimeVersion

        // set java home for each project, so they dont have to find it in the root project
        project.ext.compilerJavaHome = project.rootProject.ext.compilerJavaHome
        project.ext.runtimeJavaHome = project.rootProject.ext.runtimeJavaHome
        project.ext.compilerJavaVersion = project.rootProject.ext.compilerJavaVersion
        project.ext.runtimeJavaVersion = project.rootProject.ext.runtimeJavaVersion
        project.ext.javaVersions = project.rootProject.ext.javaVersions
        project.ext.inFipsJvm = project.rootProject.ext.inFipsJvm
        project.ext.gitRevision = project.rootProject.ext.gitRevision
        project.ext.buildDate = project.rootProject.ext.buildDate
        project.ext.gradleJavaVersion = project.rootProject.ext.gradleJavaVersion
        project.ext.java9Home = project.rootProject.ext.java9Home
    }

    static void requireDocker(final Task task) {
    }

    protected static void checkDockerVersionRecent(String dockerVersion) {
    }

    private static void throwDockerRequiredException(final String message) {
        throw new GradleException(
                message + "\nyou can address this by attending to the reported issue, "
                        + "removing the offending tasks from being executed, "
                        + "or by passing -Dbuild.docker=false")
    }

    private static String findCompilerJavaHome() {
        String compilerJavaHome = System.getenv('JAVA_HOME')
        final String compilerJavaProperty = System.getProperty('compiler.java')
        if (compilerJavaProperty != null) {
            compilerJavaHome = findJavaHome(compilerJavaProperty)
        }
        if (compilerJavaHome == null) {
            if (System.getProperty("idea.executable") != null || System.getProperty("eclipse.launcher") != null) {
                // IntelliJ does not set JAVA_HOME, so we use the JDK that Gradle was run with
                return Jvm.current().javaHome
            }
        }
        return compilerJavaHome
    }

    private static String findJavaHome(String version) {
        String versionedVarName = getJavaHomeEnvVarName(version)
        String versionedJavaHome = System.getenv(versionedVarName);
        if (versionedJavaHome == null) {
            throw new GradleException(
                    "$versionedVarName must be set to build Elasticsearch. " +
                            "Note that if the variable was just set you might have to run `./gradlew --stop` for " +
                            "it to be picked up. See https://github.com/elastic/elasticsearch/issues/31399 details."
            )
        }
        return versionedJavaHome
    }

    private static String getJavaHomeEnvVarName(String version) {
        return 'JAVA' + version + '_HOME'
    }

    /** Add a check before gradle execution phase which ensures java home for the given java version is set. */
    static void requireJavaHome(Task task, int version) {
        Project rootProject = task.project.rootProject // use root project for global accounting
        if (rootProject.hasProperty('requiredJavaVersions') == false) {
            rootProject.rootProject.ext.requiredJavaVersions = [:]
            rootProject.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
                List<String> messages = []
                for (entry in rootProject.requiredJavaVersions) {
                    if (rootProject.javaVersions.get(entry.key) != null) {
                        continue
                    }
                    List<String> tasks = entry.value.findAll { taskGraph.hasTask(it) }.collect { "  ${it.path}" }
                    if (tasks.isEmpty() == false) {
                        messages.add("JAVA${entry.key}_HOME required to run tasks:\n${tasks.join('\n')}")
                    }
                }
                if (messages.isEmpty() == false) {
                    throw new GradleException(messages.join('\n'))
                }
                rootProject.rootProject.ext.requiredJavaVersions = null // reset to null to indicate the pre-execution checks have executed
            }
        } else if (rootProject.rootProject.requiredJavaVersions == null) {
            // check directly if the version is present since we are already executing
            if (rootProject.javaVersions.get(version) == null) {
                throw new GradleException("JAVA${version}_HOME required to run task:\n${task}")
            }
        } else {
            rootProject.requiredJavaVersions.getOrDefault(version, []).add(task)
        }
    }

    /** A convenience method for getting java home for a version of java and requiring that version for the given task to execute */
    static String getJavaHome(final Task task, final int version) {
        requireJavaHome(task, version)
        return task.project.javaVersions.get(version)
    }

    private static String findRuntimeJavaHome(final String compilerJavaHome) {
        String runtimeJavaProperty = System.getProperty("runtime.java")
        if (runtimeJavaProperty != null) {
            return findJavaHome(runtimeJavaProperty)
        }
        return System.getenv('RUNTIME_JAVA_HOME') ?: compilerJavaHome
    }

    /** Finds printable java version of the given JAVA_HOME */
    private static String findJavaVersionDetails(Project project, String javaHome) {
        String versionInfoScript = 'print(' +
            'java.lang.System.getProperty("java.vendor.version", java.lang.System.getProperty("java.vendor")) + " " + ' +
            'java.lang.System.getProperty("java.version") + " [" +' +
            'java.lang.System.getProperty("java.vm.name") + " " + ' +
            'java.lang.System.getProperty("java.vm.version") + "]");'
        return runJavaAsScript(project, javaHome, versionInfoScript).trim()
    }

    /** Finds the parsable java specification version */
    private static String findJavaSpecificationVersion(Project project, String javaHome) {
        String versionScript = 'print(java.lang.System.getProperty("java.specification.version"));'
        return runJavaAsScript(project, javaHome, versionScript)
    }

    private static String findJavaVendor(Project project, String javaHome) {
        String vendorScript = 'print(java.lang.System.getProperty("java.vendor.version", System.getProperty("java.vendor"));'
        return runJavaAsScript(project, javaHome, vendorScript)
    }

    /** Finds the parsable java specification version */
    private static String findJavaVersion(Project project, String javaHome) {
        String versionScript = 'print(java.lang.System.getProperty("java.version"));'
        return runJavaAsScript(project, javaHome, versionScript)
    }

    /** Runs the given javascript using jjs from the jdk, and returns the output */
    private static String runJavaAsScript(Project project, String javaHome, String script) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            // gradle/groovy does not properly escape the double quote for windows
            script = script.replace('"', '\\"')
        }
        File jrunscriptPath = new File(javaHome, 'bin/jrunscript')
        ExecResult result = project.exec {
            executable = jrunscriptPath
            args '-e', script
            standardOutput = stdout
            errorOutput = stderr
            ignoreExitValue = true
        }
        if (result.exitValue != 0) {
            project.logger.error("STDOUT:")
            stdout.toString('UTF-8').eachLine { line -> project.logger.error(line) }
            project.logger.error("STDERR:")
            stderr.toString('UTF-8').eachLine { line -> project.logger.error(line) }
            result.rethrowFailure()
        }
        return stdout.toString('UTF-8').trim()
    }

    /** Return the configuration name used for finding transitive deps of the given dependency. */
    private static String transitiveDepConfigName(String groupId, String artifactId, String version) {
        return "_transitive_${groupId}_${artifactId}_${version}"
    }

    /**
     * Makes dependencies non-transitive.
     *
     * Gradle allows setting all dependencies as non-transitive very easily.
     * Sadly this mechanism does not translate into maven pom generation. In order
     * to effectively make the pom act as if it has no transitive dependencies,
     * we must exclude each transitive dependency of each direct dependency.
     *
     * Determining the transitive deps of a dependency which has been resolved as
     * non-transitive is difficult because the process of resolving removes the
     * transitive deps. To sidestep this issue, we create a configuration per
     * direct dependency version. This specially named and unique configuration
     * will contain all of the transitive dependencies of this particular
     * dependency. We can then use this configuration during pom generation
     * to iterate the transitive dependencies and add excludes.
     */
    static void configureConfigurations(Project project) {
        // we want to test compileOnly deps!
        project.configurations.testCompile.extendsFrom(project.configurations.compileOnly)

        // we are not shipping these jars, we act like dumb consumers of these things
        if (project.path.startsWith(':test:fixtures') || project.path == ':build-tools') {
            return
        }
        // fail on any conflicting dependency versions
        project.configurations.all({ Configuration configuration ->
            if (configuration.name.startsWith('_transitive_')) {
                // don't force transitive configurations to not conflict with themselves, since
                // we just have them to find *what* transitive deps exist
                return
            }
            if (configuration.name.endsWith('Fixture')) {
                // just a self contained test-fixture configuration, likely transitive and hellacious
                return
            }
            configuration.resolutionStrategy {
                failOnVersionConflict()
            }
        })

        // force all dependencies added directly to compile/testCompile to be non-transitive, except for ES itself
        Closure disableTransitiveDeps = { Dependency dep ->
            if (dep instanceof ModuleDependency && !(dep instanceof ProjectDependency)
                    && dep.group.startsWith('org.elasticsearch') == false) {
                dep.transitive = false

                // also create a configuration just for this dependency version, so that later
                // we can determine which transitive dependencies it has
                String depConfig = transitiveDepConfigName(dep.group, dep.name, dep.version)
                if (project.configurations.findByName(depConfig) == null) {
                    project.configurations.create(depConfig)
                    project.dependencies.add(depConfig, "${dep.group}:${dep.name}:${dep.version}")
                }
            }
        }

        project.configurations.compile.dependencies.all(disableTransitiveDeps)
        project.configurations.testCompile.dependencies.all(disableTransitiveDeps)
        project.configurations.compileOnly.dependencies.all(disableTransitiveDeps)

        project.plugins.withType(ShadowPlugin).whenPluginAdded {
            Configuration bundle = project.configurations.create('bundle')
            bundle.dependencies.all(disableTransitiveDeps)
        }
    }

    /** Adds repositories used by ES dependencies */
    static void configureRepositories(Project project) {
        project.getRepositories().all { repository ->
            if (repository instanceof MavenArtifactRepository) {
                final MavenArtifactRepository maven = (MavenArtifactRepository) repository
                assertRepositoryURIIsSecure(maven.name, project.path, maven.getUrl())
                repository.getArtifactUrls().each { uri -> assertRepositoryURIIsSecure(maven.name, project.path, uri) }
            } else if (repository instanceof IvyArtifactRepository) {
                final IvyArtifactRepository ivy = (IvyArtifactRepository) repository
                assertRepositoryURIIsSecure(ivy.name, project.path, ivy.getUrl())
            }
        }
        RepositoryHandler repos = project.repositories
        if (System.getProperty("repos.mavenLocal") != null) {
            // with -Drepos.mavenLocal=true we can force checking the local .m2 repo which is
            // useful for development ie. bwc tests where we install stuff in the local repository
            // such that we don't have to pass hardcoded files to gradle
            repos.mavenLocal()
        }
        repos.maven {
            name "elastic"
            url "https://artifacts.elastic.co/maven"
        }
        repos.mavenCentral()
        String luceneVersion = VersionProperties.lucene
        if (luceneVersion.contains('-snapshot')) {
            // extract the revision number from the version with a regex matcher
            String revision = (luceneVersion =~ /\w+-snapshot-([a-z0-9]+)/)[0][1]
            repos.maven {
                name 'lucene-snapshots'
                url "https://s3.amazonaws.com/download.elasticsearch.org/lucenesnapshots/${revision}"
            }
        }
    }

    static void assertRepositoryURIIsSecure(final String repositoryName, final String projectPath, final URI uri) {
        if (uri != null && ["file", "https", "s3"].contains(uri.getScheme()) == false) {
            final String message = String.format(
                    Locale.ROOT,
                    "repository [%s] on project with path [%s] is not using a secure protocol for artifacts on [%s]",
                    repositoryName,
                    projectPath,
                    uri.toURL())
            // throw new GradleException(message)
        }
    }

    /**
     * Returns a closure which can be used with a MavenPom for fixing problems with gradle generated poms.
     *
     * <ul>
     *     <li>Remove transitive dependencies. We currently exclude all artifacts explicitly instead of using wildcards
     *         as Ivy incorrectly translates POMs with * excludes to Ivy XML with * excludes which results in the main artifact
     *         being excluded as well (see https://issues.apache.org/jira/browse/IVY-1531). Note that Gradle 2.14+ automatically
     *         translates non-transitive dependencies to * excludes. We should revisit this when upgrading Gradle.</li>
     *     <li>Set compile time deps back to compile from runtime (known issue with maven-publish plugin)</li>
     * </ul>
     */
    private static Closure fixupDependencies(Project project) {
        return { XmlProvider xml ->
            // first find if we have dependencies at all, and grab the node
            NodeList depsNodes = xml.asNode().get('dependencies')
            if (depsNodes.isEmpty()) {
                return
            }

            // check each dependency for any transitive deps
            for (Node depNode : depsNodes.get(0).children()) {
                String groupId = depNode.get('groupId').get(0).text()
                String artifactId = depNode.get('artifactId').get(0).text()
                String version = depNode.get('version').get(0).text()

                // fix deps incorrectly marked as runtime back to compile time deps
                // see https://discuss.gradle.org/t/maven-publish-plugin-generated-pom-making-dependency-scope-runtime/7494/4
                boolean isCompileDep = project.configurations.compile.allDependencies.find { dep ->
                    dep.name == depNode.artifactId.text()
                }
                if (depNode.scope.text() == 'runtime' && isCompileDep) {
                    depNode.scope*.value = 'compile'
                }

                // remove any exclusions added by gradle, they contain wildcards and systems like ivy have bugs with wildcards
                // see https://github.com/elastic/elasticsearch/issues/24490
                NodeList exclusionsNode = depNode.get('exclusions')
                if (exclusionsNode.size() > 0) {
                    depNode.remove(exclusionsNode.get(0))
                }

                // collect the transitive deps now that we know what this dependency is
                String depConfig = transitiveDepConfigName(groupId, artifactId, version)
                Configuration configuration = project.configurations.findByName(depConfig)
                if (configuration == null) {
                    continue // we did not make this dep non-transitive
                }
                Set<ResolvedArtifact> artifacts = configuration.resolvedConfiguration.resolvedArtifacts
                if (artifacts.size() <= 1) {
                    // this dep has no transitive deps (or the only artifact is itself)
                    continue
                }

                // we now know we have something to exclude, so add exclusions for all artifacts except the main one
                Node exclusions = depNode.appendNode('exclusions')
                for (ResolvedArtifact artifact : artifacts) {
                    ModuleVersionIdentifier moduleVersionIdentifier = artifact.moduleVersion.id;
                    String depGroupId = moduleVersionIdentifier.group
                    String depArtifactId = moduleVersionIdentifier.name
                    // add exclusions for all artifacts except the main one
                    if (depGroupId != groupId || depArtifactId != artifactId) {
                        Node exclusion = exclusions.appendNode('exclusion')
                        exclusion.appendNode('groupId', depGroupId)
                        exclusion.appendNode('artifactId', depArtifactId)
                    }
                }
            }
        }
    }

    /**Configuration generation of maven poms. */
    public static void configurePomGeneration(Project project) {
        // Only works with  `enableFeaturePreview('STABLE_PUBLISHING')`
        // https://github.com/gradle/gradle/issues/5696#issuecomment-396965185
        project.tasks.withType(GenerateMavenPom.class) { GenerateMavenPom generatePOMTask ->
            // The GenerateMavenPom task is aggressive about setting the destination, instead of fighting it,
            // just make a copy.
            generatePOMTask.ext.pomFileName = null
            doLast {
                project.copy {
                    from generatePOMTask.destination
                    into "${project.buildDir}/distributions"
                    rename {
                        generatePOMTask.ext.pomFileName == null ?
                            "${project.archivesBaseName}-${project.version}.pom" :
                            generatePOMTask.ext.pomFileName
                    }
                }
            }
            // build poms with assemble (if the assemble task exists)
            Task assemble = project.tasks.findByName('assemble')
            if (assemble && assemble.enabled) {
                assemble.dependsOn(generatePOMTask)
            }
        }
        project.plugins.withType(MavenPublishPlugin.class).whenPluginAdded {
            project.publishing {
                publications {
                    all { MavenPublication publication -> // we only deal with maven
                        // add exclusions to the pom directly, for each of the transitive deps of this project's deps
                        publication.pom.withXml(fixupDependencies(project))
                    }
                }
            }
            project.plugins.withType(ShadowPlugin).whenPluginAdded {
                project.publishing {
                    publications {
                        nebula(MavenPublication) {
                            artifacts = [ project.tasks.shadowJar ]
                        }
                    }
                }
            }
        }
    }

    /**
     * Add dependencies that we are going to bundle to the compile classpath.
     */
    static void configureSourceSets(Project project) {
        project.plugins.withType(ShadowPlugin).whenPluginAdded {
            ['main', 'test'].each {name ->
                SourceSet sourceSet = project.sourceSets.findByName(name)
                if (sourceSet != null) {
                    sourceSet.compileClasspath += project.configurations.bundle
                }
            }
        }
    }

    /** Adds compiler settings to the project */
    static void configureCompile(Project project) {
        if (project.compilerJavaVersion < JavaVersion.VERSION_1_10) {
            project.ext.compactProfile = 'compact3'
        } else {
            project.ext.compactProfile = 'full'
        }
        project.afterEvaluate {
            project.tasks.withType(JavaCompile) {
                final JavaVersion targetCompatibilityVersion = JavaVersion.toVersion(it.targetCompatibility)
                final compilerJavaHomeFile = new File(project.compilerJavaHome)
                // we only fork if the Gradle JDK is not the same as the compiler JDK
                if (compilerJavaHomeFile.canonicalPath == Jvm.current().javaHome.canonicalPath) {
                    options.fork = false
                } else {
                    options.fork = true
                    options.forkOptions.javaHome = compilerJavaHomeFile
                }
                if (targetCompatibilityVersion == JavaVersion.VERSION_1_8) {
                    // compile with compact 3 profile by default
                    // NOTE: this is just a compile time check: does not replace testing with a compact3 JRE
                    if (project.compactProfile != 'full') {
                        options.compilerArgs << '-profile' << project.compactProfile
                    }
                }
                /*
                 * -path because gradle will send in paths that don't always exist.
                 * -missing because we have tons of missing @returns and @param.
                 * -serial because we don't use java serialization.
                 */
                // don't even think about passing args with -J-xxx, oracle will ask you to submit a bug report :)
                // fail on all javac warnings
                options.compilerArgs << '-Werror' << '-Xlint:all,-path,-serial,-options,-deprecation' << '-Xdoclint:all' << '-Xdoclint:-missing'

                // either disable annotation processor completely (default) or allow to enable them if an annotation processor is explicitly defined
                if (options.compilerArgs.contains("-processor") == false) {
                    options.compilerArgs << '-proc:none'
                }

                options.encoding = 'UTF-8'
                options.incremental = true

                // TODO: use native Gradle support for --release when available (cf. https://github.com/gradle/gradle/issues/2510)
                options.compilerArgs << '--release' << targetCompatibilityVersion.majorVersion
            }
            // also apply release flag to groovy, which is used in build-tools
            project.tasks.withType(GroovyCompile) {
                final compilerJavaHomeFile = new File(project.compilerJavaHome)
                // we only fork if the Gradle JDK is not the same as the compiler JDK
                if (compilerJavaHomeFile.canonicalPath == Jvm.current().javaHome.canonicalPath) {
                    options.fork = false
                } else {
                    options.fork = true
                    options.forkOptions.javaHome = compilerJavaHomeFile
                    options.compilerArgs << '--release' << JavaVersion.toVersion(it.targetCompatibility).majorVersion
                }
            }
        }
    }

    static void configureJavadoc(Project project) {
        // remove compiled classes from the Javadoc classpath: http://mail.openjdk.java.net/pipermail/javadoc-dev/2018-January/000400.html
        final List<File> classes = new ArrayList<>()
        project.tasks.withType(JavaCompile) { javaCompile ->
            classes.add(javaCompile.destinationDir)
        }
        project.tasks.withType(Javadoc) { javadoc ->
            javadoc.executable = new File(project.compilerJavaHome, 'bin/javadoc')
            javadoc.classpath = javadoc.getClasspath().filter { f ->
                return classes.contains(f) == false
            }
            /*
             * Generate docs using html5 to suppress a warning from `javadoc`
             * that the default will change to html5 in the future.
             */
            javadoc.options.addBooleanOption('html5', true)
        }
        configureJavadocJar(project)
    }

    /** Adds a javadocJar task to generate a jar containing javadocs. */
    static void configureJavadocJar(Project project) {
        Jar javadocJarTask = project.task('javadocJar', type: Jar)
        javadocJarTask.classifier = 'javadoc'
        javadocJarTask.group = 'build'
        javadocJarTask.description = 'Assembles a jar containing javadocs.'
        javadocJarTask.from(project.tasks.getByName(JavaPlugin.JAVADOC_TASK_NAME))
        project.assemble.dependsOn(javadocJarTask)
    }

    static void configureSourcesJar(Project project) {
        Jar sourcesJarTask = project.task('sourcesJar', type: Jar)
        sourcesJarTask.classifier = 'sources'
        sourcesJarTask.group = 'build'
        sourcesJarTask.description = 'Assembles a jar containing source files.'
        sourcesJarTask.from(project.sourceSets.main.allSource)
        project.assemble.dependsOn(sourcesJarTask)
    }

    /** Adds additional manifest info to jars */
    static void configureJars(Project project) {
        project.ext.licenseFile = null
        project.ext.noticeFile = null
        project.tasks.withType(Jar) { Jar jarTask ->
            // we put all our distributable files under distributions
            jarTask.destinationDir = new File(project.buildDir, 'distributions')
            // fixup the jar manifest
            jarTask.doFirst {
                // this doFirst is added before the info plugin, therefore it will run
                // after the doFirst added by the info plugin, and we can override attributes
                jarTask.manifest.attributes(
                        // TODO: remove using the short hash
                        'Change': ((String)project.gitRevision).substring(0, 7),
                        'X-Compile-Elasticsearch-Version': VersionProperties.elasticsearch.replace("-SNAPSHOT", ""),
                        'X-Compile-Lucene-Version': VersionProperties.lucene,
                        'X-Compile-Elasticsearch-Snapshot': VersionProperties.isElasticsearchSnapshot(),
                        'Build-Date': project.buildDate,
                        'Build-Java-Version': project.compilerJavaVersion)
                // Force manifest entries that change by nature to a constant to be able to compare builds more effectively
                if (System.properties.getProperty("build.compare_friendly", "false") == "true") {
                    jarTask.manifest.getAttributes().clear()
                }
            }
        }
        project.plugins.withType(ShadowPlugin).whenPluginAdded {
            /*
             * When we use the shadow plugin we entirely replace the
             * normal jar with the shadow jar so we no longer want to run
             * the jar task.
             */
            project.tasks.jar.enabled = false
            project.tasks.shadowJar {
                /*
                 * Replace the default "shadow" classifier with null
                 * which will leave the classifier off of the file name.
                 */
                classifier = null
                /*
                 * Not all cases need service files merged but it is
                 * better to be safe
                 */
                mergeServiceFiles()
                /*
                 * Bundle dependencies of the "bundled" configuration.
                 */
                configurations = [project.configurations.bundle]
            }
            // Make sure we assemble the shadow jar
            project.tasks.assemble.dependsOn project.tasks.shadowJar
            project.artifacts {
                apiElements project.tasks.shadowJar
            }
        }
    }

    static void applyCommonTestConfig(Project project) {
    }

    private static String findDefaultParallel(Project project) {
        if (project.file("/proc/cpuinfo").exists()) {
            // Count physical cores on any Linux distro ( don't count hyper-threading )
            Map<String, Integer> socketToCore = [:]
            String currentID = ""
            project.file("/proc/cpuinfo").readLines().forEach({ line ->
                if (line.contains(":")) {
                    List<String> parts = line.split(":", 2).collect({it.trim()})
                    String name = parts[0], value = parts[1]
                    // the ID of the CPU socket
                    if (name == "physical id") {
                        currentID = value
                    }
                    // Number  of cores not including hyper-threading
                    if (name == "cpu cores") {
                        assert currentID.isEmpty() == false
                        socketToCore[currentID] = Integer.valueOf(value)
                        currentID = ""
                    }
                }
            })
            return socketToCore.values().sum().toString();
        } else if ('Mac OS X'.equals(System.getProperty('os.name'))) {
            // Ask macOS to count physical CPUs for us
            ByteArrayOutputStream stdout = new ByteArrayOutputStream()
            project.exec {
                executable 'sysctl'
                args '-n', 'hw.physicalcpu'
                standardOutput = stdout
            }
            return stdout.toString('UTF-8').trim();
        }
        return 'auto';
    }

    private static String gitRevision(final Project project) {
        try {
            /*
             * We want to avoid forking another process to run git rev-parse HEAD. Instead, we will read the refs manually. The
             * documentation for this follows from https://git-scm.com/docs/gitrepository-layout and https://git-scm.com/docs/git-worktree.
             *
             * There are two cases to consider:
             *  - a plain repository with .git directory at the root of the working tree
             *  - a worktree with a plain text .git file at the root of the working tree
             *
             * In each case, our goal is to parse the HEAD file to get either a ref or a bare revision (in the case of being in detached
             * HEAD state).
             *
             * In the case of a plain repository, we can read the HEAD file directly, resolved directly from the .git directory.
             *
             * In the case of a worktree, we read the gitdir from the plain text .git file. This resolves to a directory from which we read
             * the HEAD file and resolve commondir to the plain git repository.
             */
            final Path dotGit = project.getRootProject().getRootDir().toPath().resolve(".git");
            String revision;
            if (Files.exists(dotGit) == false) {
                return "unknown";
            }
            final Path head;
            final Path gitDir;
            if (Files.isDirectory(dotGit)) {
                // this is a git repository, we can read HEAD directly
                head = dotGit.resolve("HEAD");
                gitDir = dotGit;
            } else {
                // this is a git worktree, follow the pointer to the repository
                final Path workTree = Paths.get(readFirstLine(dotGit).substring("gitdir:".length()).trim());
                head = workTree.resolve("HEAD");
                final Path commonDir = Paths.get(readFirstLine(workTree.resolve("commondir")));
                if (commonDir.isAbsolute()) {
                    gitDir = commonDir;
                } else {
                    // this is the common case
                    gitDir = workTree.resolve(commonDir);
                }
            }
            final String ref = readFirstLine(head);
            if (ref.startsWith("ref:")) {
                String refName = ref.substring("ref:".length()).trim()
                Path refFile = gitDir.resolve(refName)
                if (Files.exists(refFile)) {
                    revision = readFirstLine(refFile)
                } else if (Files.exists(dotGit.resolve("packed-refs"))) {
                    // Check packed references for commit ID
                    Pattern p = Pattern.compile("^([a-f1-9]{40}) " + refName + "\$")
                    Stream<String> lines = Files.lines(dotGit.resolve("packed-refs"));
                    try {
                        revision = lines.map( { s -> p.matcher(s) })
                                .filter( { m -> m.matches() })
                                .map({ m -> m.group(1) })
                                .findFirst()
                                .orElseThrow({ -> new IOException("Packed reference not found for refName " + refName) });
                    } finally {
                        lines.close()
                    }
                } else {
                    throw new GradleException("Can't find revision for refName " + refName);
                }
            } else {
                // we are in detached HEAD state
                revision = ref;
            }
            return revision;
        } catch (final IOException e) {
            // for now, do not be lenient until we have better understanding of real-world scenarios where this happens
            throw new GradleException("unable to read the git revision", e);
        }
    }

    private static String readFirstLine(final Path path) throws IOException {
        Stream lines =  Files.lines(path, StandardCharsets.UTF_8)
        try {
            return Files.lines(path, StandardCharsets.UTF_8)
                    .findFirst()
                    .orElseThrow(
                            new Supplier<IOException>() {

                                @Override
                                IOException get() {
                                    return new IOException("file [" + path + "] is empty");
                                }

                            });
        } finally {
            lines.close()
        }
    }

    /** Configures the test task */
    static Task configureTest(Project project) {
        project.tasks.getByName('test') {
            include '**/*Tests.class'
        }
    }

    private static configurePrecommit(Project project) {
    }

    private static configureDependenciesInfo(Project project) {
    }
}
