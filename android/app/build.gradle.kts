plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Version is injected by CI from git tags: -PversionName=1.2.3 -PversionCode=42
// Developers never edit versionName/versionCode manually.
val injectedVersionName = (project.findProperty("versionName") as String?)?.removePrefix("v")
val injectedVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
val defaultVersionName = "1.0.0"
val defaultVersionCode = 1

android {
    namespace = "com.uchat.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uchat.android"
        minSdk = 26
        targetSdk = 35
        versionCode = injectedVersionCode ?: defaultVersionCode
        versionName = injectedVersionName ?: defaultVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // debugSymbolLevel = "SYMBOL_TABLE"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // ABI-specific release APKs are produced by GitHub Actions (see .github/workflows/release.yml).
    // x86 is intentionally excluded: Ubuntu 24.04 has no i386 rootfs and no x86 jniLibs are
    // bundled, so an x86 APK would install but could never start a shell.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Release signing credentials come from CI secrets (env vars).
    // If they are absent the APK is left unsigned and CI generates an
    // ephemeral keystore so artifacts remain installable for testing.
    val envKeystorePath = System.getenv("UCHAT_KEYSTORE_PATH")
    val envKeystorePassword = System.getenv("UCHAT_KEYSTORE_PASSWORD")
    val envKeyAlias = System.getenv("UCHAT_KEY_ALIAS")
    val envKeyPassword = System.getenv("UCHAT_KEY_PASSWORD")

    signingConfigs {
        if (envKeystorePath != null && file(envKeystorePath).exists()) {
            create("release") {
                storeFile = file(envKeystorePath)
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Android 10+ (targetSdk >= 29) forbids exec() of binaries stored in the writable app
        // data directory (W^X policy). proot therefore ships as libproot.so and must be EXTRACTED
        // to nativeLibraryDir, where exec() is allowed. useLegacyPackaging=true makes the package
        // manager materialize the .so files on disk instead of loading them straight from the APK.
        jniLibs {
            useLegacyPackaging = true
            // proot is a prebuilt static executable, not a build artifact — leave it untouched.
            keepDebugSymbols += "**/libproot.so"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable +=
            listOf(
                "GradleDependency",
                "AndroidGradlePluginVersion",
                "ExpiredTargetSdkVersion",
                "VectorPath",
                "IconDuplicates",
                "LockedOrientationActivity",
            )
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
