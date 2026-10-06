plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    // Push notifications (Phase 6) are fully implemented in
    // notifications/CentinelFirebaseMessagingService.kt — this is the one
    // remaining manual step, since it needs YOUR Firebase project's file.
    // Uncomment once you add app/google-services.json (see android/README.md):
    // id("com.google.gms.google-services")
}

android {
    namespace = "com.centinel.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.centinel.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
        }
        create("prod") {
            dimension = "environment"
            // REPLACE with your actual production HTTPS endpoint
            buildConfigField("String", "API_BASE_URL", "\"https://api.centinel-cyber.com/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // Inherits from flavor
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lottie for animations
    implementation("com.airbnb.android:lottie-compose:6.4.1")


    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // DataStore (secure-ish local token storage)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.1.0")

    // CameraX + ML Kit barcode scanning (QR Scanner module)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Firebase (optional — requires google-services.json; safe to leave unused)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Accompanist permissions (camera permission for QR scanner)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Vico — Cartesian (column/bar) charts for the Analytics screen.
    // Version 2.1.2 is the last stable (non-alpha, non-multiplatform-preview)
    // release line as of this writing, and its Compose Cartesian-chart API
    // (CartesianChartHost / rememberCartesianChart / VerticalAxis.rememberStart /
    // HorizontalAxis.rememberBottom) is what AnalyticsScreen.kt uses below.
    // NOTE: dependency resolution could not be verified in this environment —
    // this sandbox has no network access to Google's/Maven Central's Gradle
    // repositories. Run `./gradlew :app:dependencies` after pulling this
    // change to confirm it resolves, and bump the version if a newer 2.x/3.x
    // patch release is out by the time you build.
    implementation("com.patrykandpatrick.vico:compose:2.1.2")
    implementation("com.patrykandpatrick.vico:compose-m3:2.1.2")
    implementation("com.patrykandpatrick.vico:core:2.1.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
