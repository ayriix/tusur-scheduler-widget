plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional shared signing key supplied only by GitHub Actions environment variables.
// Local Android Studio builds continue to use the developer's normal debug.keystore.
val sharedKeystoreFile = System.getenv("TUSUR_SIGNING_KEYSTORE_FILE")
val sharedKeystorePassword = System.getenv("TUSUR_SIGNING_STORE_PASSWORD")
val sharedKeyAlias = System.getenv("TUSUR_SIGNING_KEY_ALIAS")
val sharedKeyPassword = System.getenv("TUSUR_SIGNING_KEY_PASSWORD")

android {
    namespace = "ru.tusur.schedulewidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.tusur.schedulewidget"
        minSdk = 23
        targetSdk = 35
        versionCode = 23
        versionName = "2.5.13"
    }

    if (!sharedKeystoreFile.isNullOrBlank()) {
        signingConfigs {
            create("sharedUpdateKey") {
                storeFile = file(sharedKeystoreFile)
                storePassword = sharedKeystorePassword
                keyAlias = sharedKeyAlias
                keyPassword = sharedKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!sharedKeystoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("sharedUpdateKey")
            }
        }

        getByName("release") {
            // R8 removes unused code and optimizes bytecode in the release APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Use the same certificate as Debug in CI so existing installations
            // signed with the shared update key can be upgraded in place.
            if (!sharedKeystoreFile.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("sharedUpdateKey")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jsoup:jsoup:1.18.3")
}
