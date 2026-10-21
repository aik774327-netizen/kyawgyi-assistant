# KyawGyi asset download and runtime setup

## Build-time download

Copy `tools/assets.example.json` to `tools/assets.json`, replace every placeholder URL and SHA-256 with values from a trusted immutable release, and run:

```bash
python3 tools/download_all_assets.py \
  --manifest tools/assets.json \
  --project-root .
```

The script writes Sherpa files to `app/src/main/assets/models/myanmar/`, the Vosk ZIP to `app/src/main/assets/models/vosk/`, and the Sherpa AAR to `app/libs/sherpa-onnx.aar`. It will not accept HTTP URLs, unknown hashes, or a mismatched downloaded artifact.

## `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.kyawgyi.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kyawgyi.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // The AAR downloaded by download_all_assets.py lives in app/libs.
    repositories { flatDir { dirs("libs") } }
}

dependencies {
    implementation(files("libs/sherpa-onnx.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<pin>")
    implementation("com.squareup.okhttp3:okhttp:<pin>")
    implementation("androidx.core:core-ktx:<pin>")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:<pin>")
    implementation("com.alphacephei:vosk-android:<pin>")
}
```

If the Sherpa release publishes a Maven artifact instead of an AAR, prefer the pinned Maven coordinate over `flatDir`. Do not download a moving `latest` URL during a reproducible build.

## Runtime usage

```kotlin
val downloader = ModelDownloader(applicationContext)
val config = RuntimeModelConfig(
    sherpaZipUrl = BuildConfig.MYANMAR_SHERPA_URL,
    sherpaZipSha256 = BuildConfig.MYANMAR_SHERPA_SHA256,
    voskZipUrl = BuildConfig.VOSK_URL,
    voskZipSha256 = BuildConfig.VOSK_SHA256,
)

serviceScope.launch {
    try {
        downloader.ensure(config)
        // Only now construct/select an engine that reads filesDir/models.
        check(downloader.isSherpaReady())
        sttEngineManager.select(SttEngineId.SHERPA_ONNX)
        sttEngineManager.start()
    } catch (error: Throwable) {
        // Observe downloader.status and choose a local Vosk/System fallback.
        sttEngineManager.select(SttEngineId.AUTO)
    }
}
```

UI can observe `downloader.status`:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
            downloader.status.collect { status ->
                when (status) {
                    DownloadStatus.IDLE -> Unit
                    is DownloadStatus.DOWNLOADING -> progress.value = status.progressPercent
                    DownloadStatus.EXTRACTING -> message.value = "Installing model…"
                    DownloadStatus.COMPLETED -> message.value = "Models ready"
                    is DownloadStatus.ERROR -> message.value = status.message
                }
            }
        }
    }
}
```

Keep the model URLs and hashes in remote-config only if that configuration is authenticated and signed. For a simpler trusted release, compile the URL and SHA-256 into `BuildConfig` or a signed app resource. Never trust a SHA-256 value downloaded from the same unauthenticated endpoint as the model.
