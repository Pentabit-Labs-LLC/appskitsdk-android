plugins {
    id("com.android.library") version "8.5.0" apply false
    id("maven-publish")
}

repositories {
    google()
    mavenCentral()
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.github.TalhaChaudhry"
                artifactId = "aks-dist"
                version = "1.0.3"

                artifact("libs/AppsKitSDK_v5000.aar")
                artifact("libs/AppsKitSDKSupport_v101.aar")
            }
        }
    }
}
