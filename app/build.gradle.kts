plugins {
    id("com.android.application")
}

android {
    namespace = "com.navis.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.navis.mobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.6"
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

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
