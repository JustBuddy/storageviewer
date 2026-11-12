import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// One version for the whole app, set in gradle.properties. Release tags (v1.2.3) must match it.
val appVersion = providers.gradleProperty("storageviewer.version").get()
val appVersionCode = appVersion.split('.').map { it.toInt() }.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "storageviewer.version parts above 99 do not fit the version code" }
    major * 10_000 + minor * 100 + patch
}

// Release signing: keystore.properties next to settings.gradle.kts locally (gitignored), or
// SIGNING_* environment variables in CI. Without either, release builds use the debug key.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(property: String, env: String): String? = keystoreProperties.getProperty(property) ?: System.getenv(env)

android {
    namespace = "wtf.buddyworks.storageviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "wtf.buddyworks.storageviewer"
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        val storeFilePath = signingValue("storeFile", "SIGNING_STORE_FILE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug").also {
                logger.warn("w: No release key (keystore.properties or SIGNING_* variables): release builds are signed with the debug key.")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.window)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
