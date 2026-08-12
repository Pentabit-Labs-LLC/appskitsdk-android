# AppsKitSDK (AKS) — Android Integration Guide
[![](https://jitpack.io/v/Pentabit-Labs-LLC/appskitsdk-android.svg)](https://jitpack.io/#Pentabit-Labs-LLC/appskitsdk-android)

This guide covers everything you need to integrate AppsKitSDK (AKS) into your Android app: dependencies, manifest setup, ProGuard rules, SDK initialization, calling every ad format, events and logging, local storage, local notifications, and the built-in debug tools.

AKS is distributed as a prebuilt AAR via JitPack — add the dependency and repository as shown below; there's no manual AAR download involved.

---

## 1. Dependencies

### 1.1 The AKS SDK

Add the SDK itself in your **app-level** `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Pentabit-Labs-LLC:appskitsdk-android:${LATEST_VERSION}")
}
```

Replace `${LATEST_VERSION}` with the latest tag from the [appskitsdk-android releases](https://github.com/Pentabit-Labs-LLC/appskitsdk-android) (or the JitPack badge in that repo's README), e.g. `v6.4.0.0`.

AKS is published as a set of raw `.aar` files (not a dependency-aware Maven module), so **none of its third-party dependencies come along transitively**. Everything below has to be declared explicitly in your app module. The one exception is `SupportAKS` (`com.pentabit.aks_android_support_lib`) — those classes are bundled directly inside the `appskitsdk-android` artifact itself, so you don't add `com.pentabit:aks-android-helpers` separately.

### 1.2 Required libraries (always needed)

These are referenced directly by AKS's compiled code, so they're required no matter which ad networks you actually enable:

```kotlin
dependencies {
    // Firebase (Remote Config drives almost all AKS behavior; Analytics is used for AKS events)
    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.android.gms:play-services-measurement-api:23.0.0")

    // Coroutines / serialization / datetime — used throughout AdsManager and AppsKitSDK
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Ktor — AKS's internal network client for fetching ad/config data
    implementation("io.ktor:ktor-client-core:3.3.3")
    implementation("io.ktor:ktor-client-okhttp:3.3.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
    implementation("io.ktor:ktor-client-logging:3.3.3")

    // Local storage AKS uses for its own preferences/session/ad-state tracking
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
    implementation("com.russhwolf:multiplatform-settings-serialization:1.1.1")

    // Jetpack Compose — AKS's Feature/Cross-Promotion ad screens, the Configuration
    // Dashboard, and the Test Suite (§10) are all built with Compose. Pull these in via
    // the Compose BOM so versions stay aligned with whatever else in your app uses Compose.
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Image loading used by AKS's built-in cross-promotion / feature-promotion ad screens
    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose-core:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-ktor3:3.3.0")
    implementation("media.kamel:kamel-image:0.9.5")

    // Used by enableAppAutoUpdate() in the AKS base Activities
    implementation("com.google.android.play:app-update:2.1.0")
    // Powers AppsKitSDKUtils.askForRating() — AKS's built-in in-app review flow
    implementation("com.google.android.play:review:2.0.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // IronSource core — AKS's base Activity classes call IronSource.onResume()/onPause()
    // unconditionally on every screen, so this is required even if you never select
    // IronSource as an ad network in the AKS portal.
    implementation("com.ironsource.sdk:mediationsdk:8.5.0")
}
```

> Keep the Ktor version consistent across every `ktor-*` artifact — mixing Ktor 2.x and 3.x artifacts on the same classpath causes binary-incompatibility crashes at runtime.
>
> Match AKS's coroutines (`1.7.3`), serialization (`1.9.0`), and Firebase BOM (`34.3.0`) versions across the rest of your app too, rather than letting an older version linger elsewhere in your dependency graph. Firebase's BOM has had breaking changes between major versions (`33.x` → `34.x`), and Gradle silently resolving to whichever version "wins" doesn't guarantee the AKS code paths that call into these libraries stay binary-compatible — these are required, not best-effort suggestions.

### 1.3 Mobile measurement partner (MMP) SDKs (always needed)

AKS auto-initializes these three from keys set in the AKS portal / Firebase Remote Config — you don't call into them directly, but their SDK classes are referenced unconditionally inside AKS's compiled code, so they must be on your classpath:

```kotlin
dependencies {
    // AppsFlyer
    implementation("com.appsflyer:af-android-sdk:6.13.0")
    implementation("com.appsflyer:adrevenue:6.9.0")
    implementation("com.miui.referrer:homereferrer:1.0.0.6")
    implementation("com.android.installreferrer:installreferrer:2.2")

    // Adjust
    implementation("com.adjust.sdk:adjust-android:4.38.3")

    // Solar Engine (Reyun)
    implementation("com.reyun.solar.engine.oversea:solar-engine-core:1.3.1.4")
}
```

If you don't have keys for one of these in your AKS portal config, that's fine — AKS simply skips initializing it at runtime. The dependency still needs to be present for the app to build.

### 1.4 Ad network / mediation SDKs

AKS bundles handler code for AdMob, AppLovin MAX, and Chartboost Mediation (Helium) inside the same AAR, in addition to IronSource (already listed above). Which network actually serves an ad is decided remotely (per ad format, with failover), so the safest approach — and the one Pentabit's own reference app uses — is to include all of them:

```kotlin
dependencies {
    // ---- AdMob / Google Mobile Ads + mediation adapters ----
    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.google.ads.mediation:applovin:13.0.1.0")
    implementation("com.google.ads.mediation:ironsource:8.5.0.1")
    implementation("com.google.ads.mediation:chartboost:9.8.2.0")
    implementation("com.google.ads.mediation:vungle:7.4.2.0")
    implementation("com.google.ads.mediation:facebook:6.18.0.0")
    implementation("com.google.ads.mediation:mintegral:16.9.71.0")
    implementation("com.google.ads.mediation:pangle:6.4.0.4.0")
    implementation("com.google.ads.mediation:unity:4.12.5.0") {
        exclude(group = "com.google.android.gms", module = "play-services-cronet")
    }

    // ---- AppLovin MAX + adapters ----
    implementation("com.applovin:applovin-sdk:13.3.0")
    implementation("com.applovin.mediation:google-adapter:+")
    implementation("com.applovin.mediation:google-ad-manager-adapter:+")
    implementation("com.applovin.mediation:ironsource-adapter:8.7.0.0.0")
    implementation("com.applovin.mediation:facebook-adapter:+")
    implementation("com.applovin.mediation:vungle-adapter:+")
    implementation("com.applovin.mediation:mintegral-adapter:+")
    implementation("com.applovin.mediation:bytedance-adapter:6.5.0.8.1")
    implementation("com.applovin.mediation:unityads-adapter:+") {
        exclude(group = "com.google.android.gms", module = "play-services-cronet")
    }

    // ---- Chartboost Mediation (Helium) + adapters ----
    implementation("com.chartboost:chartboost-mediation-sdk:4.7.1")
    implementation("com.chartboost:chartboost-mediation-adapter-admob:4.22.3.0.4")
    implementation("com.chartboost:chartboost-mediation-adapter-applovin:4.12.0.0.0")
    implementation("com.chartboost:chartboost-mediation-adapter-ironsource:4.7.5.2.0.0")
    implementation("com.chartboost:chartboost-mediation-adapter-vungle:4.7.1.0.0")
    implementation("com.chartboost:chartboost-mediation-adapter-meta-audience-network:4.6.16.0.0")
    implementation("com.chartboost:chartboost-mediation-adapter-pangle:4.5.5.0.3.0")
}
```

If you're certain a given network (e.g. Helium) will never be enabled in your AKS portal config, you can drop its block — just know that AKS's own internal `AdNetwork` failover logic can route to any of ADMOB, GAM, MAX, IRON_SOURCE, or HELIUM per ad format, so trimming this list reduces flexibility on the remote-config side.

---

## 2. AndroidManifest.xml

AKS's own manifest only declares:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

These merge into your app automatically via Gradle's manifest merger — **you don't need to add them yourself.**

What you **do** need to add to your own `AndroidManifest.xml` — both the AdMob App ID `meta-data` tag and the registration of your custom `Application` subclass (see [§5](#5-initializing-the-sdk-and-adsmanager)) go on the same `<application>` element:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".YourApplication">

        <!-- Required if AdMob/GAM is one of your enabled ad networks. Use your own
             AdMob App ID from the AdMob console — it can't be bundled inside AKS
             since it's specific to your app. -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />

        <!-- keep your existing <activity>, <service>, <provider>, etc. declarations here -->

    </application>

</manifest>
```

Merge the `android:name` attribute and the `meta-data` tag into your existing `<application>` element rather than pasting this as a second one — a manifest can only have one `<application>` tag.

---

## 3. settings.gradle

Add the JitPack repository so Gradle can resolve the AKS coordinate. In a modern Android Studio project, repositories live in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

If your project still declares repositories at the project level instead (older template, `allprojects { repositories { ... } }` in the root `build.gradle.kts`), add it there instead:

```kotlin
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

---

## 4. ProGuard rules

The AKS AAR ships with an **empty** consumer ProGuard file, so none of the rules below are applied automatically — you need to add them yourself. Create/extend `app/proguard-rules.pro` with:

```proguard
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes Annotation
-keepattributes *Annotation*

# Kotlin / Kotlinx
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep,includedescriptorclasses class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class kotlinx.serialization.internal.** { *; }

# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-keep interface io.ktor.** { *; }

# Compose (avoids recomposition crashes after minification)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Google Play Services / Firebase / AdMob
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep class com.google.android.gms.ads.mediation.** { *; }
-dontwarn com.google.android.gms.ads.mediation.**
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final * NULL;
}
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-keepnames class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
    com.google.android.gms.ads.identifier.AdvertisingIdClient$Info getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
    java.lang.String getId();
    boolean isLimitAdTrackingEnabled();
}

# Mediation adapters (AdMob/MAX bidding partners)
-keep class com.google.ads.mediation.unity.** { *; }
-keep class com.google.ads.mediation.vungle.** { *; }
-keep class com.google.ads.mediation.facebook.** { *; }
-keep class com.google.ads.mediation.applovin.** { *; }
-keep class com.bytedance.sdk.openadsdk.** { *; }
-keep class com.facebook.** { *; }
-keep class com.chartboost.heliumsdk.** { *; }
-keep class com.chartboost.mediation.** { *; }

# Attribution / MMP SDKs
-keep class com.reyun.** { *; }
-keep interface com.reyun.** { *; }
-dontwarn com.reyun.**
-keep class route.** { *; }
-keep interface route.** { *; }
-keep public class com.android.installreferrer.** { *; }

# org.json (used by several mediation/MMP SDKs)
-keep class org.json.** { *; }
-dontwarn org.json.**

# Gson (only needed if you also use Gson elsewhere in your app)
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# OEM referrer plugins (only relevant on Huawei/Honor devices)
-keep class com.huawei.hms.** { *; }
-keep class com.hihonor.** { *; }
```

Wire it into your `app/build.gradle.kts`:

```kotlin
android {
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## 5. Initializing the SDK and AdsManager

### 5.1 Application class

Extend `AppsKitSDKApplication` and implement its five abstract members:

```kotlin
class YourApplication : AppsKitSDKApplication() {

    // Encrypted default-config JSON to fall back on before Remote Config is fetched.
    // Provided to you from the AKS portal for your app.
    override fun setAKSDefaultConfigs(): String = "<encrypted-config-json-from-aks-portal>"

    override fun isTestMode(): Boolean = BuildConfig.DEBUG

    override fun isDevMode(): Boolean = BuildConfig.DEBUG

    override fun setPlatform(): Platform = Platform.GOOGLE // or Platform.AMAZON

    override fun onConfigsReadyToUse(remoteConfig: FirebaseRemoteConfig) {
        // Called once Firebase Remote Config has been fetched/activated.
        // Optional hook if you need to read custom remote-config keys yourself.
    }
}
```

`onCreate()` in the base class already does the heavy lifting for you — it calls `AppsKitSDK.initialize/setPlatform/setTestMode/setDevMode`, fetches and saves the default config, kicks off `manageRemoteConfigs()`, and calls `AppsKitSDK.initMMP()` to wire up AppsFlyer/Adjust/Solar Engine. You don't need to call any of that yourself.

### 5.2 Initializing without extending `AppsKitSDKApplication`

Extending `AppsKitSDKApplication` isn't mandatory — it's a convenience wrapper. If your app already has its own `Application` base class you can't give up, you can call the same `AppsKitSDK` methods it calls internally, directly from your own `Application.onCreate()`:

```kotlin
class YourApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        AppsKitSDK.initialize(PlatformContext(this), this)
        AppsKitSDK.setPlatform(Platform.GOOGLE) // or Platform.AMAZON
        AppsKitSDK.setTestMode(BuildConfig.DEBUG)
        AppsKitSDK.setDevMode(BuildConfig.DEBUG)
        AppsKitSDK.setTimeToFetchRemoteConfigInSeconds(3600) // optional, defaults to 3600

        AppsKitSDK.saveDefaultConfigJson("<encrypted-config-json-from-aks-portal>")
        AppsKitSDK.manageRemoteConfigs(object : OnRemoteConfigAvailable {
            override fun onRemoteConfigReadyToFetch(remoteConfig: FirebaseRemoteConfig) {
                // optional — your own hook into the fetched remote config
            }
        })

        AppsKitSDK.initMMP() // wires up AppsFlyer / Adjust / Solar Engine from remote-config keys

        // AppsKitSDKApplication does this for you via ActivityLifecycleCallbacks. Without it,
        // AppsKitSDK has no "current activity" — native-ad preloading and any code that calls
        // AppsKitSDK.getCurrentActivity() won't have a target to work with.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                AppsKitSDK.setCurrentActivity(PlatformActivity(activity))
                Adjust.onResume() // keeps Adjust session tracking accurate
            }
            override fun onActivityPaused(activity: Activity) {
                Adjust.onPause()
            }
            override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
```

What you give up by not extending the base class: the internet-connectivity broadcast/dialog handling, and the built-in "App Open ad at application level" flow (`requestSplashAppOpenAd`/`showAppOpenAd`, triggered automatically from `onStart`) — those are instance methods of `AppsKitSDKApplication` itself, not exposed on the `AppsKitSDK` object. If you still want an App Open ad on cold start, call `AdsManager.loadAppOpen`/`AdsManager.showAppOpen` yourself (see [§6](#6-calling-the-ad-formats)) at the point in your own startup flow where you want it.

### 5.3 Activities

Extend `AppsKitSDKBaseActivity` (for `AppCompatActivity`-based screens) or `AppsKitSDKBaseComponentActivity` (for plain `ComponentActivity`/Compose-only screens):

```kotlin
class HomeActivity : AppsKitSDKBaseActivity() {

    override fun setScreenNameAndId(): Pair<Int, String> = Pair(1, "Home")

    override fun enableAppAutoUpdate(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // required — this drives ad-network init, preloading,
                                            // session/day tracking, and the auto SCREEN event
        setContent { /* your Compose UI */ }
    }
}
```

Extending these base classes automatically gives you: ad-network initialization and preloading on first screen, IronSource lifecycle forwarding in `onResume`/`onPause`, session/day-count tracking, an internet-connectivity dialog, optional in-app-update prompts, and `sendAKSEvent(...)` for firing events tied to this screen (see [§7](#7-aks-events-and-logs)). (Adjust's own `onResume`/`onPause` forwarding happens at the `Application` level — see [§5.2](#52-initializing-without-extending-appskitsdkapplication) — not here.)

#### Internet connectivity dialog

Both base Activities observe connectivity in `onResume()` and, by default, show a blocking "no internet" dialog whenever the connection drops, dismissing it automatically on reconnect. This is on for every screen unless you turn it off:

```kotlin
class HomeActivity : AppsKitSDKBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showInternetConnectivity(false) // opt this screen out of the automatic dialog
    }

    // Called on every connectivity change, dialog or not — hook your own UI here
    override fun onInternetConnectivityChange(isInternetAvailable: Boolean?) {
        // e.g. show/hide your own offline banner
    }

    // Optional — return an anchor view to get a "back online" Snackbar when the
    // dialog dismisses on reconnect. Returns null (no Snackbar) by default.
    override fun getSnackBarView(): View? = findViewById(R.id.rootLayout)
}
```

> When you call `showInternetConnectivity(false)`, AKS still calls `onInternetConnectivityChange`, but it always passes `true` regardless of the actual connection state — it doesn't track real connectivity once you've opted out of managing it. Drive your own offline UI off your own connectivity check if you need accurate state here.

#### Portrait-only restriction

To lock a screen to portrait, call `restrictPortraitOnly()` (protected, available on both base Activities):

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    restrictPortraitOnly()
}
```

This calls `setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT)` on your behalf — except on Android 8.0 (API 26), where it's skipped on purpose, since `setRequestedOrientation` is known to crash on translucent/floating-themed activities on that specific OS version.

### 5.4 Consent and ad initialization

Once you've collected consent (e.g. via Google's UMP/CMP flow), tell AKS so it knows ads are allowed to show:

```kotlin
AdsManager.initializeAds(userHasGivenConsent = true, isCCPAConsent = true)
```

### 5.5 Remove Ads / IAP gating

If your app sells a "remove ads" purchase, wire it through AKS rather than gating ad calls yourself — every load/show call already checks this flag internally:

```kotlin
AppsKitSDK.setRemoveAdsStatus(true)   // call after a successful purchase/restore
AppsKitSDK.getRemoveAdsStatus()       // check current status
```

---

## 6. Calling the ad formats

Every ad call takes a **placeholder** — a string key configured against an ad format in the AKS portal (e.g. `"1"`). AKS resolves the placeholder to a primary ad network plus a failover list at runtime; you never reference an ad-unit ID directly in code.

### Interstitial

```kotlin
// Load only
AdsManager.loadInterstitial(context, placeholder, object : AdsCallback() {
    override fun onLoaded() { }
    override fun onFailedToLoad() { }
    override fun onAdShown() { }
    override fun onAdDismissed() { }
    override fun onAdFailedToShow() { }
})

// Show a previously loaded interstitial
AdsManager.showInterstitial(activity, placeholder, callback)

// Load and show in one call
AdsManager.loadAndShowInterstitialAd(activity, placeholder, callback)

// Check availability before showing
AdsManager.isInterstitialAvailable(placeholder)
```

### Rewarded

```kotlin
AdsManager.loadRewarded(context, placeholder, object : RewardedAdCallbacks() {
    override fun onRewardedLoaded() { }
    override fun onRewardedAdLoadFailure() { }
    override fun onRewardedCompleted() { }
    override fun onAdRewarded() { }
    override fun onAdRewardedAdDismissed() { }
    override fun onRewardedFailedToShow() { }
})

AdsManager.showRewarded(activity, placeholder, callbacks)

// Load and show in one call — simpler success/fail callback
AdsManager.loadAndShowRewardedAd(activity, placeholder, object : RewardedLoadAndShowCallback {
    override fun onRewardedAdSuccess() { }
    override fun onRewardedAdFailed() { }
})

AdsManager.isRewardedAvailable(placeholder)
```

### App Open

```kotlin
AdsManager.loadAppOpen(application, placeholder, object : AppOpenAdCallbacks() {
    override fun onLoaded() { }
    override fun onFailedToLoad() { }
    override fun onDismiss() { }
    override fun onAdShown() { }
    override fun onAdFailToShow() { }
})

AdsManager.showAppOpen(activity, placeholder, callbacks)
```

> If you want AKS's own splash/app-open handling (driven by the `isAppOpenAtApplicationLvl` remote-config flag) instead of managing App Open yourself, use `loadDefaultAppOpen` / `showDefaultAppOpen` on your `AppsKitSDKApplication` subclass — or simply call `requestSplashAppOpenAd(callbacks, addInDefaultDelay)`, which the base `Application` class already implements end-to-end.

### Banner

```kotlin
AdsManager.loadBanner(context, placeholder, object : BannerCallback() {
    override fun onBannerLoaded() { }
    override fun onBannerFailedToLoad() { }
    override fun onBannerClicked() { }
    override fun onBannerShown() { }
    override fun onBannerSizeChanged(bannerSizes: BannerSizes) { }
    override fun setBannerSize(width: Double, height: Double) { }
})

// layout is a FrameLayout (BannerContainer) you've placed in your screen
AdsManager.showBanner(activity, layout, placeholder, callback)
```

`BannerSizes`: `BANNER` (320×50), `LARGE_BANNER` (320×100), `MEDIUM_RECTANGLE` (300×250), `FULL_BANNER` (468×60).

### Native

```kotlin
AdsManager.loadNative(activity, placeholder, isShowInScrollView = false, object : NativeCallback() {
    override fun onNativeLoaded() { }
    override fun onNativeFailedToLoad() { }
    override fun onNativeClicked() { }
    override fun onNativeShown() { }
})

// frameLayout is the BannerContainer that will host the native ad view; res is an
// optional custom native-ad layout resource (pass null to use AKS's default layout)
AdsManager.showNative(activity, frameLayout, placeholder, isShowInScrollView = false, res = null, callback)

// Show the native ad using the design you created for this placeholder in the
// AKS portal, instead of a layout resource — see below.
AdsManager.showTemplatedNative(activity, frameLayout, placeholder, isShowInScrollView = false, callback)
```

`showTemplatedNative` is for placeholders where the native ad's look was designed and assigned **on the AKS portal** rather than in your app's code — there's no `res` param at all, because the layout isn't yours to supply. AKS resolves which portal template applies to this placeholder, fetches it (cached after the first fetch), and renders the native ad with it through the same load/display pipeline `showNative` uses. If no template is assigned to the placeholder, or it can't be fetched, AKS falls back to its own built-in default native design rather than failing the ad — so it's always safe to call, even before a template has been set up on the portal side.

### Feature Promotion

Feature Promotion is AKS's own house-ad format for cross-promoting one feature of your app to users who haven't unlocked it yet — it only resolves through the `AKS` network (not AdMob/MAX/etc.), and the promoted items are configured server-side in the AKS portal, scoped to the placeholder.

```kotlin
// Optional — exclude features the user already has unlocked from being promoted
AdsManager.setFeaturesAvailable(listOf("premium_theme", "no_ads"))

AdsManager.showFeaturePromotion(activity, placeholder, object : OnFeaturePromotionClicked {
    override fun onFeaturePromotionClicked(targetScreen: String) {
        // user tapped the promotion — navigate them to targetScreen
    }

    override fun onFailToShowFeaturePromotion(errorMessage: String) { }
})
```

---

## 7. AKS Events and logs

### 7.1 Automatic screen events

If your Activity overrides `setScreenNameAndId()` (returning a `Pair<screenId, screenName>`), the base Activity automatically fires a `SCREEN` event in `onCreate()` — you don't need to call anything yourself for that.

### 7.2 Manual events from within an Activity

From inside any Activity extending `AppsKitSDKBaseActivity` / `AppsKitSDKBaseComponentActivity`:

```kotlin
sendAKSEvent(AppsKitSDKEventType.BUTTON, "SaveClicked")
```

This builds and logs an event named `e_{screenId}_{prefix}_{name}` to Firebase Analytics (and the debug log) using the screen ID from `setScreenNameAndId()`.

`sendAKSEvent` is just a convenience wrapper — it expands to the lines below, which is what you'd call directly from somewhere that doesn't have it available (a Fragment, ViewModel, Composable, or a plain Activity that isn't extending one of the AKS base Activities):

```kotlin
AKSLogManager.log(
    PlatformContext(this),
    AKSEventCreator.createEvent(screenId, eventType, name)
)
```

`screenId` here is just the `Int` you'd otherwise get from `setScreenNameAndId().first` — track/pass it yourself if you're not inside an AKS base Activity.

`AppsKitSDKEventType` and its Firebase event-name prefix:

| Enum constant | Prefix |
|---|---|
| `SCREEN` | `SCR` |
| `BUTTON` | `BTN` |
| `STATUS` | `STS` |
| `ITEM` | `ITM` |
| `LIST` | `LST` |
| `ICON` | `ICO` |
| `DIALOG_BTN` | `DLG_BTN` |
| `ACTION` | `ACT` |
| `TAB` | `TAB` |
| `DIALOG` | `DLG` |

Firebase Analytics caps event names at 40 characters, so keep `name` short — especially for `ITEM`/`LIST` events, which append an index/title to the name.

### 7.3 General-purpose logging and events

For events outside an Activity's screen scope (services, ViewModels, repositories, etc.), use the plain-`Context` wrapper `AppsKitSDKLogManager`:

```kotlin
// Free-form debug log (tagged PTB_LOG in Logcat)
AppsKitSDKLogManager.log(AppsKitSDKLogType.INFO, "Sync finished")

// Fire a named Firebase event with parameters, from anywhere
AppsKitSDKLogManager.sendEvent("custom_event_name", mapOf("key" to "value"))

// Purchase / subscription events
AppsKitSDKLogManager.sendPurchaseEvent(
    context = this,
    purchaseType = PurchaseType.SUBSCRIPTION, // or PurchaseType.IN_APP
    productId = "premium_monthly",
    price = 4.99,
    currency = "USD",
    receiptId = receipt,
    uniqueTransactionId = transactionId,
    startDate = startDateIso,
    endDate = endDateIso
)

// SDK build/version banner in Logcat
AppsKitSDKLogManager.logAarVersion()
```

`AppsKitSDKLogType`: `INFO`, `ERROR`, `VERBOSE`, `WARNING`, `DESCRIPTION`.

### 7.4 Debug log tags

Filter Logcat by:

- `PTB_LOG` — general SDK logs
- `PTB_LOG_ADS` — ad load/show lifecycle
- `PTB_LOG_ANALYTICS` — events sent to Firebase/MMPs

---

## 8. Local preferences (`PreferencesManager`)

AKS also exposes the simple key-value store it uses internally (backed by `multiplatform-settings`, i.e. `SharedPreferences` under the hood on Android), so you can use it for your own app's lightweight preferences instead of pulling in another storage dependency:

```kotlin
// Strings, Ints, Booleans, Longs, Floats
PreferencesManager.addInPreferences("user_name", "Talha")
PreferencesManager.addInPreferences("launch_count", 5)
PreferencesManager.addInPreferences("onboarding_done", true)
PreferencesManager.addInPreferencesAsLong("last_sync_time", System.currentTimeMillis())
PreferencesManager.addInPreferencesAsFloat("scroll_position", 12.5f)

PreferencesManager.getStringPreferences("user_name", "")
PreferencesManager.getIntegerPreferences("launch_count", 0)
PreferencesManager.getBooleanPreferences("onboarding_done", false)
PreferencesManager.getLongPreferences("last_sync_time", 0L)
PreferencesManager.getFloatPreferences("scroll_position", 0f)

// Remove a single key
PreferencesManager.flushSavedCache("user_name")

// Store/retrieve a serializable object as JSON
PreferencesManager.addInPreferences("user_profile", profile, UserProfile.serializer())
PreferencesManager.getCustomPreference("user_profile", UserProfile.serializer())
```

This is the same store AKS uses for its own internal flags (like `REMOVE_ADS`), so keys you choose should be namespaced/distinct from the ones documented elsewhere in this guide to avoid collisions.

---

## 9. Local notifications (`AppsKitSDKLocalNotificationManager`)

AKS ships a small local-notification toolkit — a queue, a notification builder, and an abstract `BroadcastReceiver` — that you can use to show your own local notifications. AKS does **not** register the receiver in its manifest or schedule anything for you; both of those are on you.

### 9.1 Manifest

Subclass `AppsKitSDKLocalNotificationBroadCastReceiver` (shown in [§9.2](#92-queuing-a-notification)) and declare your subclass as a `<receiver>`, merged into the same `<application>` element from [§2](#2-androidmanifestxml):

```xml
<application
    android:name=".YourApplication">

    <receiver
        android:name=".YourNotificationReceiver"
        android:exported="false" />

</application>
```

If you target Android 13+ (API 33), also add the runtime notification permission — AKS's own manifest doesn't declare this, so it won't get merged in for you:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`POST_NOTIFICATIONS` is a runtime permission, so you still need to request it from the user (e.g. via `ActivityResultContracts.RequestPermission()`) before notifications will actually post.

### 9.2 Queuing a notification

Subclass `AppsKitSDKLocalNotificationBroadCastReceiver` and implement `checkNotifications()` — your hook to decide whether a notification is due, called every time the receiver fires:

```kotlin
class YourNotificationReceiver : AppsKitSDKLocalNotificationBroadCastReceiver() {

    override fun checkNotifications() {
        addNotificationInQueue(
            NotificationModel(
                id = "daily_reminder",                      // dedup key — a second add with the same id is ignored
                title = "Come back!",
                description = "You have unfinished items waiting.",
                icon = R.drawable.ic_notification,
                notificationId = 1001,                       // the actual Android notification ID passed to notify()
                targetScreen = "com.yourapp.MainActivity"    // fully-qualified Activity class opened on tap
            )
        )
    }
}
```

Right after `checkNotifications()` returns, `onReceive` automatically calls `AppsKitSDKLocalNotificationManager.showNotification(context)` — it pops the oldest queued `NotificationModel` and displays it. You don't call `showNotification` yourself in this flow; queuing inside `checkNotifications()` is enough.

> This queued-show path checks `POST_NOTIFICATIONS` itself on Android 13+ and silently skips (logging a line) if it isn't granted, so make sure you've requested it first per [§9.1](#91-manifest).

### 9.3 Scheduling

AKS doesn't register or trigger this receiver on any schedule — you decide when `onReceive` fires, typically with `AlarmManager`:

```kotlin
val intent = Intent(context, YourNotificationReceiver::class.java)
val pendingIntent = PendingIntent.getBroadcast(
    context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
alarmManager.setRepeating(
    AlarmManager.RTC_WAKEUP,
    System.currentTimeMillis() + intervalMs,
    intervalMs,
    pendingIntent
)
```

For `intervalMs`, you can hard-code your own cadence, or honor the value configured in the AKS portal's remote config (`HOURS_TO_MAKE_NOTIFICATION`, defaults to 2 hours if unset). That value is exposed as `getTimeIntervalInMilliSeconds()` on the base receiver class, but it's `protected`, so call it from inside your subclass rather than from the call site above:

```kotlin
class YourNotificationReceiver : AppsKitSDKLocalNotificationBroadCastReceiver() {
    fun intervalMillis(): Long = getTimeIntervalInMilliSeconds()
    override fun checkNotifications() { /* ... */ }
}
```

> `AlarmManager.setRepeating` is inexact under Doze/battery optimizations — if you need it to fire reliably while idle, reschedule with `setExactAndAllowWhileIdle` on every trigger instead, or move to `WorkManager`'s `PeriodicWorkRequest` (15-minute minimum interval). AKS doesn't prescribe either; pick whichever fits your app. Also note alarms don't survive a device reboot unless you re-register them yourself (e.g. from a `BOOT_COMPLETED` receiver).

### 9.4 Showing a notification directly (no queue)

To post a notification immediately, without going through the queue/receiver flow above:

```kotlin
// Tapping opens the Activity at classPath
AppsKitSDKLocalNotificationManager.showNotification(
    context = this,
    id = 2001,
    classPath = "com.yourapp.MainActivity",
    channelId = "general",
    title = "New message",
    message = "You've got a new message waiting.",
    notificationIcon = R.drawable.ic_notification
)

// Or pass a fully-built Intent instead of a class path
AppsKitSDKLocalNotificationManager.showNotification(
    context = this,
    id = 2002,
    intent = Intent(this, MainActivity::class.java).putExtra("from", "notification"),
    channelId = "general",
    title = "New message",
    message = "Tap to view details.",
    notificationIcon = R.drawable.ic_notification
)
```

Both overloads create the notification channel for you if it doesn't already exist. Unlike the queued path in [§9.2](#92-queuing-a-notification), neither checks `POST_NOTIFICATIONS` itself — request the runtime permission yourself on Android 13+, or the call may silently fail to post.

---

## 10. The built-in Configuration Dashboard / Test Suite

AKS bundles a Compose-based diagnostics screen — there's no separate debug build or manifest entry for it; it renders as a full-screen dialog on top of whatever Activity you launch it from. It's useful for checking what AKS thinks is going on (remote config, ad states, consent) without attaching a debugger.

### Opening it

Call this from anywhere you have an Activity reference — a hidden row in your own settings screen, a long-press on your app's version number, a debug-only button, whatever trigger you wire up (AKS doesn't add a trigger for you):

```kotlin
AppsKitSDK.showGodModeScreen(PlatformActivity(this))
```

This opens the **AKS Configuration Dashboard**, from which you can check:

- **Enable Ads** — a toggle, and **GDPR Status** — a read-only `TRUE`/`FALSE` reflecting `AdsManager.isUserConsentsProvided()`.
- **Check Configurations** — opens the Test Suite itself (see below).
- **Placeholder Calls** — a pretty-printed JSON dump of ad call activity per placeholder.
- **Placeholder Load Stats** — load attempt/success/failure counters per placeholder.
- **AKS Logs** — the same accumulated log history you'd otherwise read from Logcat (see [§11](#11-debugging-with-aks-logs-ptb_log)), searchable on-screen.

### The Test Suite

Tapping **Check Configurations** opens the Test Suite screen, showing: test mode / dev mode flags, whether the default (offline) config or the fetched remote config is currently active, the AKS SDK version, the raw remote-config JSON AKS fetched, and live state + last-shown time for Interstitial, Rewarded, and App Open, plus the current session count and day count.

You can also jump straight there without going through the dashboard:

```kotlin
AppsKitSDK.showAKSTestSuit(PlatformActivity(activity))
```

Both calls only need a `PlatformActivity` — AKS reads everything else (ad states, config, session data) internally.

---

## 11. Debugging with AKS logs (`PTB_LOG`)

Every log line AKS produces — from `AKSLogManager`/`AppsKitSDKLogManager` calls, ad load/show events, and internal SDK activity — goes to two places at once: Logcat, tagged by category, and the in-memory log accumulator that backs the **AKS Logs** screen in [§10](#10-the-built-in-configuration-dashboard--test-suite).

### Filtering Logcat

Filter by tag in Android Studio's Logcat (`tag:PTB_LOG`) or via `adb`:

```bash
adb logcat -s PTB_LOG:* PTB_LOG_ADS:* PTB_LOG_ANALYTICS:*
```

- `PTB_LOG` — general SDK logs (init sequence, mode banners, lifecycle, errors)
- `PTB_LOG_ADS` — ad load/show lifecycle for every format
- `PTB_LOG_ANALYTICS` — events sent to Firebase/MMPs, including every `sendAKSEvent`/`AKSLogManager.log` call from [§7](#7-aks-events-and-logs)

### Reading logs without Logcat

If you're debugging a release build, a tester's device, or a support ticket where attaching `adb` isn't an option, open the Test Suite's **AKS Logs** screen instead ([§10](#10-the-built-in-configuration-dashboard--test-suite)) — it shows the exact same log lines on-device, with on-screen search, so a tester can scroll/search it directly or screenshot it back to you.

> `AppsKitSDKLogManager.logAarVersion()` (see [§7.3](#73-general-purpose-logging-and-events)) is the fastest way to confirm which AKS AAR version is actually integrated when you suspect a version mismatch — it prints the version banner to `PTB_LOG`.
