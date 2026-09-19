import java.util.Properties

gradle.taskGraph.whenReady {
    allTasks.forEach { task ->
        val name = task.name
        if ((name.endsWith("Debug") || name.contains("DebugUnitTest") || name.contains("DebugAndroidTest") || name.startsWith("assembleDebug") || name.startsWith("installDebug")) && !name.contains("DebugSymbols") && !name.contains("DebugMetadata")) {
            throw GradleException("HARD REPO RULE VIOLATION: Debug task '$name' is forbidden in this project. Profile variant only.")
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Read the Unsplash access key from local.properties (gitignored) so it never
// lands in source control. Absent key → empty string → bundled stills are used.
val unsplashAccessKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("unsplash.accessKey", "")
val googlePhotosClientId: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("googlePhotos.clientId", "")
val captionServerUrl: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("caption.serverUrl", "")
val captionServerToken: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("caption.serverToken", "")
val releaseSigningProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.livingroomhq"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.livingroomhq"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"

        buildConfigField("String", "UNSPLASH_ACCESS_KEY", "\"$unsplashAccessKey\"")
        buildConfigField("String", "GOOGLE_PHOTOS_CLIENT_ID", "\"$googlePhotosClientId\"")
        buildConfigField("String", "CAPTION_SERVER_URL", "\"$captionServerUrl\"")
        buildConfigField("String", "CAPTION_SERVER_TOKEN", "\"$captionServerToken\"")
        // M1 PCM tap PoC WAV writer. Off by default; ASR pipeline owns capture now.
        buildConfigField("boolean", "PCM_CAPTURE_POC", "false")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-O3"
                arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_PLATFORM=android-28")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = releaseSigningProps.getProperty("releaseStoreFile")
            require(storeFilePath.isNullOrBlank() || !storeFilePath.contains("debug.keystore")) {
                "releaseStoreFile points at a debug keystore ($storeFilePath). Release builds " +
                    "must be signed with a real release key outside the repo."
            }
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = releaseSigningProps.getProperty("releaseStorePassword")
                keyAlias = releaseSigningProps.getProperty("releaseKeyAlias")
                keyPassword = releaseSigningProps.getProperty("releaseKeyPassword")
            } else {
                logger.warn(
                    "WARNING: no release signing config in local.properties; " +
                        "release builds will fail to package.",
                )
            }
        }
        create("projectDebug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("projectDebug")
            buildConfigField("boolean", "PCM_CAPTURE_POC", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "PCM_CAPTURE_POC", "false")
        }
        create("profile") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("projectDebug")
            matchingFallbacks.add("release")
            buildConfigField("boolean", "PCM_CAPTURE_POC", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:widget"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.tv.material)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
