plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.pentabit.aksdist"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    publishing {
        singleVariant("release")
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.TalhaChaudhry"
                artifactId = "aks-dist"
                version = "1.0.1"
            }
        }
    }

    // Include your .aar files
    val aarFiles = file("$projectDir/libs").listFiles { _, name ->
        name.endsWith(".aar")
    } ?: emptyArray()

    aarFiles.forEach { aar ->
        println("Including AAR: ${aar.name}")
        artifacts.add("default", aar)
    }
}
