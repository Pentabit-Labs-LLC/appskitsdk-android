plugins {
    id("maven-publish")
}

repositories {
    google()
    mavenCentral()
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
