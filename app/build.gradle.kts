import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is driven by key.properties at the repo root. It is only present
// on release builds (CI writes it from secrets); local debug builds don't need it.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

// Amadeus API credentials come from local.properties (git-ignored) or the environment,
// never from source. Absent keys => the app runs on the simulated feed.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
fun secret(propKey: String, envKey: String): String =
    (localProps.getProperty(propKey) ?: System.getenv(envKey) ?: "").trim()

val amadeusClientId = secret("amadeus.clientId", "AMADEUS_CLIENT_ID")
val amadeusClientSecret = secret("amadeus.clientSecret", "AMADEUS_CLIENT_SECRET")
// "test" (free sandbox) or "production". Defaults to test.
val amadeusEnv = (localProps.getProperty("amadeus.env") ?: System.getenv("AMADEUS_ENV") ?: "test").trim()

// Travelpayouts (Aviasales) — free, token-only. Preferred free provider.
val travelpayoutsToken = secret("travelpayouts.token", "TRAVELPAYOUTS_TOKEN")

android {
    namespace = "com.travelassistant.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.travelassistant.app"
        minSdk = 26
        targetSdk = 35
        // CI injects -PversionCodeOverride=<run_number + offset>; falls back to 1 locally.
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "AMADEUS_CLIENT_ID", "\"$amadeusClientId\"")
        buildConfigField("String", "AMADEUS_CLIENT_SECRET", "\"$amadeusClientSecret\"")
        buildConfigField("String", "AMADEUS_ENV", "\"$amadeusEnv\"")
        buildConfigField("String", "TRAVELPAYOUTS_TOKEN", "\"$travelpayoutsToken\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = (keystoreProperties["storeFile"] as String?)?.let { rootProject.file(it) }
                storePassword = keystoreProperties["storePassword"] as String?
            }
        }
    }

    buildTypes {
        release {
            // Signed with the upload key when the keystore is present (CI/release);
            // otherwise falls back to the debug key so local release builds still work.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // The app is Compose-only (ComponentActivity, no Fragments); registerForActivityResult
        // here is valid and does not require androidx.fragment 1.3.0. This check is a false
        // positive triggered by a transitive fragment version.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.app.update)
    debugImplementation(libs.androidx.ui.tooling)
}
