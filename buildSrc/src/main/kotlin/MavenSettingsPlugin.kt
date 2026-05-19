import org.apache.maven.settings.Settings
import org.apache.maven.settings.building.DefaultSettingsBuilder
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest
import org.apache.maven.settings.building.SettingsBuildingException
import org.apache.maven.settings.building.SettingsBuildingRequest
import org.apache.maven.settings.building.SettingsBuildingResult
import org.gradle.api.GradleScriptException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

open class MavenSettingsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("mavenSettings", MavenSettingsExtension::class.java, project)
    }
}

open class MavenSettingsExtension(private val project: Project) {

    fun loadSettings(repository: String) {
        try {
            val settingsBuildingRequest: SettingsBuildingRequest = DefaultSettingsBuildingRequest()
            settingsBuildingRequest.userSettingsFile = project.file(File(System.getProperty("user.home"), ".m2/settings.xml"))

            // Add system properties and environment variables for interpolation
            val properties = java.util.Properties()
            properties.putAll(System.getProperties())
            properties.putAll(System.getenv())
            settingsBuildingRequest.systemProperties = properties

            val factory = DefaultSettingsBuilderFactory()
            val settingsBuilder: DefaultSettingsBuilder = factory.newInstance()
            val settingsBuildingResult: SettingsBuildingResult = settingsBuilder.build(settingsBuildingRequest)
            val settings: Settings = settingsBuildingResult.effectiveSettings

            for (server in settings.servers) {
                project.logger.info("Settings parser examining server {}", server.id)
                if (repository == server.id) {
                    if (server.username != null && server.password != null) {
                        project.logger.info("Found valid credentials for publishing")
                        // Store the values in the project ext
                        project.extensions.extraProperties.set("central_username", server.username)
                        project.extensions.extraProperties.set("central_password", server.password)
                        return
                    }
                }
            }
        } catch (e: SettingsBuildingException) {
            throw GradleScriptException("Unable to read local Maven settings.", e)
        }
        project.logger.warn("Unable to find settings for repository {}  ", repository)
        project.extensions.extraProperties.set("central_username", "")
        project.extensions.extraProperties.set("central_password", "")
    }
}

// Made with Bob
