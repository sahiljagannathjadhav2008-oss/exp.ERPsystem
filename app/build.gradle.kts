plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.builtdifferent.erp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.builtdifferent.erp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Room schema export location, used by migration tests.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    signingConfigs {
        create("release") {
            // Release signing is optional and only applied if a keystore is
            // supplied via environment variables in CI. Without it, the
            // release build falls back to being unsigned so the workflow can
            // still assemble a release artifact.
            val storeFilePath = System.getenv("ERP_RELEASE_KEYSTORE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("ERP_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ERP_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("ERP_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val storeFilePath = System.getenv("ERP_RELEASE_KEYSTORE")
            if (!storeFilePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }

    // NOTE: composeOptions.kotlinCompilerExtensionVersion is NOT used here.
    // As of Kotlin 2.0, the Jetpack Compose compiler ships as its own
    // Gradle plugin (org.jetbrains.kotlin.plugin.compose, applied above)
    // versioned in lockstep with the Kotlin compiler itself, rather than
    // as a standalone artifact selected by kotlinCompilerExtensionVersion.
    // Setting composeOptions alongside Kotlin 2.0.21 is both unnecessary
    // (the plugin already resolves the matching Compose compiler) and, in
    // some AGP/Kotlin combinations, produces a build-time warning or
    // "duplicate Compose compiler configuration" error, so it is
    // deliberately omitted rather than left in as dead configuration.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Room / SQLite
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // PDF generation (invoice rendering) — uses android.graphics.pdf, no
    // extra library needed at runtime, but iText-free lightweight layout
    // helpers live in util/pdf.

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
