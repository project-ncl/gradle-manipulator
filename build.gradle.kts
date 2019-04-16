allprojects {
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}


subprojects {

    apply(plugin = "maven-publish")
    apply(plugin = "java-gradle-plugin")

}