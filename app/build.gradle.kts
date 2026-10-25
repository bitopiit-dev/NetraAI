plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.factory.netra"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.factory.netra"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.0"
        ndk {
            // Phones/TV boxes (arm) + Android Studio emulator (x86_64). Keeps the VLC libs small.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Replace with your own release keystore before distributing.
            signingConfig = signingConfigs.getByName("debug")
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
    androidResources {
        noCompress.add("tflite")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ---------------------------------------------------------------------------
// AI model: EfficientDet-Lite0 (COCO, detects person / car / truck / bike ...)
// Downloaded automatically on first build. If the build machine is offline,
// put the file manually at app/src/main/assets/efficientdet_lite0.tflite
// ---------------------------------------------------------------------------
val modelUrl =
    "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"
val modelFile = file("src/main/assets/efficientdet_lite0.tflite")

val downloadAiModel by tasks.registering {
    description = "Downloads the EfficientDet-Lite0 AI model if missing"
    outputs.file(modelFile)
    onlyIf { !modelFile.exists() }
    doLast {
        try {
            modelFile.parentFile.mkdirs()
            uri(modelUrl).toURL().openStream().use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
            println("Netra: AI model downloaded -> ${modelFile.path}")
        } catch (e: Exception) {
            modelFile.delete()
            println(
                "Netra WARNING: could not download AI model (${e.message}). " +
                    "App will build, but AI detection stays off until the model is in assets/."
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadAiModel) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // RTSP live video (Dahua main / sub stream)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1")

    // Fallback player for streams Media3 cannot open (old / OEM Dahua firmware)
    implementation("org.videolan.android:libvlc-all:3.5.1")

    // Local database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Dahua HTTP API (digest auth implemented in-app)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device AI
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Alert snapshot images
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
