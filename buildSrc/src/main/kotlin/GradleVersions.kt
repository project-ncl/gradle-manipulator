import org.gradle.util.GradleVersion

// For some reason, using the abbreviated 'GradleVersion' (rather than 'orgg.gradle.util.GradleVersion')
// in the main build.gradle.kts file leads to unresolved references on versions less than 7.0 - i.e. using
// Kotlin 1.3 or below. Wrapping it in a buildSrc plugin avoids that and leads to a cleaner top level file.
object GradleVersions {
    val versionCurrent = GradleVersion.current()
    val version410 = GradleVersion.version("4.10")
    val version50 = GradleVersion.version("5.0")
    val version52 = GradleVersion.version("5.2")
    val version53 = GradleVersion.version("5.3")
    val version54 = GradleVersion.version("5.4")
    val version683 = GradleVersion.version("6.8.3")
    val version60 = GradleVersion.version("6.0")
    val version70 = GradleVersion.version("7.0")
    val version74 = GradleVersion.version("7.4")
    val version76 = GradleVersion.version("7.6")
    val version80 = GradleVersion.version("8.0")
    val version83 = GradleVersion.version("8.3")
    val version90 = GradleVersion.version("9.0.0")
}
