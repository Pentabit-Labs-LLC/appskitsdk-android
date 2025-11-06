# aks-dist
Prebuilt Android SDKs distributed via JitPack.

## Integration

Add this in your app-level Gradle:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.TalhaChaudhry:aks-dist:1.0.7")
}
