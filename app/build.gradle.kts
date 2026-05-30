plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // ✅ BUG 11 FIX: Removed the unused KSP plugin. There is no Room or other
    //    KSP annotation processor in this project; keeping it loaded every build
    //    slows the configuration phase and emits an "applied but not used" warning.
    alias(libs.plugins.google.services)
}

android {
    namespace  = "com.vaulto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vaulto"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "2.0"
    }

    buildTypes {
        release {
            // ✅ BUG 12 FIX: Enable minification for the release APK.
            //    A finance app shipping without R8/ProGuard has:
            //    (a) a bloated APK with all debug symbols intact, and
            //    (b) trivially reverse-engineerable business logic.
            //    The consumer-proguard-rules files from Firebase/Compose/Credentials
            //    are automatically merged, so no extra rules are needed beyond
            //    the project-level proguard-rules.pro for any custom classes.
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Google Sign-In (Credential Manager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    debugImplementation(libs.androidx.ui.tooling)
}