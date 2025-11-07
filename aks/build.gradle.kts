plugins {
    id("maven-publish")
}

repositories {
    google()
    mavenCentral()
}

// Dummy assemble task (needed for JitPack)
tasks.register("assemble") {
    group = "build"
    description = "Dummy assemble task for JitPack"
    doLast {
        println("Assemble task: nothing to build, only publishing AARs.")
    }
}

publishing {
    publications {
        create<MavenPublication>("AppsKitSDK") {
            groupId = "com.github.Pentabit-Labs-LLC"
            artifactId = "AppsKitSDK-core"
            version = "5.0.0.1"
            artifact("$projectDir/libs/AppsKitSDK_v5000.aar")
        }

        create<MavenPublication>("AppsKitSDKSupport") {
            groupId = "com.github.Pentabit-Labs-LLC"
            artifactId = "AppsKitSDK-support"
            version = "1.0.0.1"
            artifact("$projectDir/libs/AppsKitSDKSupport_v101.aar")
        }
    }
}
