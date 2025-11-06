plugins {
    // Enables Maven publishing
    `maven-publish`
}

// Group ID (what users will use in dependency)
group = "com.pentabit"
version = "1.0.0" // can be any global version label for your distribution

publishing {
    publications {
        // -----------------------------
        // 1️⃣ AppsKitSDK main library
        // -----------------------------
        create<MavenPublication>("AppsKitSDK") {
            artifact("libs/AppsKitSDK_v5000.aar")
            groupId = "com.pentabit"
            artifactId = "appskit-sdk"
            version = "5.0.0" // Matches your AAR naming for clarity

            pom {
                name.set("AppsKit SDK")
                description.set("Main AppsKit SDK library")
            }
        }

        // -----------------------------
        // 2️⃣ AppsKitSDKSupport library
        // -----------------------------
        create<MavenPublication>("AppsKitSDKSupport") {
            artifact("libs/AppsKitSDKSupport_v101.aar")
            groupId = "com.pentabit"
            artifactId = "appskit-sdk-support"
            version = "1.0.1"

            pom {
                name.set("AppsKit SDK Support")
                description.set("Support library for AppsKit SDK")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/TalhaChaudhry/aks-dist")

            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: project.findProperty("githubActor") as String?
                    ?: "TalhaChaudhry"  // fallback
                password = System.getenv("GITHUB_TOKEN")
                    ?: project.findProperty("githubToken") as String?
                    ?: "YOUR_GITHUB_TOKEN"
            }
        }
    }
}
