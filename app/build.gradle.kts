import java.util.Properties

plugins {
    id("com.android.application")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

fun signingValue(propertyName: String, environmentName: String): String? =
    System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val signingStorePath = signingValue("storeFile", "BATTERYRING_STORE_FILE")
val signingStorePassword = signingValue("storePassword", "BATTERYRING_STORE_PASSWORD")
val signingKeyAlias = signingValue("keyAlias", "BATTERYRING_KEY_ALIAS")
val signingKeyPassword = signingValue("keyPassword", "BATTERYRING_KEY_PASSWORD")
val signingConfigured = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { it != null }

android {
    namespace = "io.github.ulipo.batteryring"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ulipo.batteryring"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 4
        versionName = System.getenv("VERSION_NAME") ?: "1.4.1"
    }

    signingConfigs {
        create("batteryRing") {
            if (signingConfigured) {
                storeFile = rootProject.file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("batteryRing")
            }
        }

        release {
            isMinifyEnabled = false
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("batteryRing")
            }
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
}

if (System.getenv("CI") == "true" && !signingConfigured) {
    throw GradleException(
        "BatteryRing signing is not configured. Add BATTERYRING_STORE_FILE, " +
            "BATTERYRING_STORE_PASSWORD, BATTERYRING_KEY_ALIAS and " +
            "BATTERYRING_KEY_PASSWORD to the build environment."
    )
}
