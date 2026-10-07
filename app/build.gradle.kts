plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.ulipo.batteryring"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ulipo.batteryring"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
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
}
