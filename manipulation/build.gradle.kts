group = "org.jboss.gm.manipulation"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create("manipulationPlugin") {
            id = "gm-manipulation"
            implementationClass = "org.jboss.gm.manipulation.ManipulationPlugin"
        }
    }
}