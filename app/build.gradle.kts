import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

/**
 * The support assistant key is a demo credential. It is read from `local.properties`, which is
 * git-ignored, so it never enters the repository or the report. Anyone who has the APK can still
 * extract it, so the app also caps spend per day and falls back to an offline FAQ. A production
 * build must call our own backend instead of DeepSeek directly.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun buildConfigValue(name: String, fallback: String = ""): String =
    (localProperties.getProperty(name) ?: System.getenv(name) ?: fallback)
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.taplink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taplink.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Empty by default: the support screen then explains that the assistant is not configured
        // and offers WhatsApp plus the offline FAQ instead of failing silently.
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${buildConfigValue("DEEPSEEK_API_KEY")}\"")
        buildConfigField("String", "DEEPSEEK_MODEL", "\"${buildConfigValue("DEEPSEEK_MODEL", "deepseek-flash")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
