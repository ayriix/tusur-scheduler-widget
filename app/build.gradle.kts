plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.tusur.schedulewidget"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.tusur.schedulewidget"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
