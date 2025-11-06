plugins {
    id("maven-publish")
}

repositories {
    google()
    mavenCentral()
}

subprojects {
    apply(plugin = "maven-publish")

    repositories {
        google()
        mavenCentral()
    }
}
