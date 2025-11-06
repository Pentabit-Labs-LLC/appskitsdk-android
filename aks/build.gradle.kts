plugins {
    id("maven-publish")
}

repositories {
    google()
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("AppsKitSDK") {
            groupId = "com.github.TalhaChaudhry"
            artifactId = "AppsKitSDK"
            version = "1.0.5"
            artifact("$projectDir/libs/AppsKitSDK_v5000.aar")
        }

        create<MavenPublication>("AppsKitSDKSupport") {
            groupId = "com.github.TalhaChaudhry"
            artifactId = "AppsKitSDKSupport"
            version = "1.0.5"
            artifact("$projectDir/libs/AppsKitSDKSupport_v101.aar")
        }
    }
}
