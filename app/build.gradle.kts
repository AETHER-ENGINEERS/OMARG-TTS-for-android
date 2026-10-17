import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aetherengineers.omarg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aetherengineers.omarg"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"

        ndk {
            // Galaxy S22 and every phone from the last decade is arm64; x86_64 keeps the emulator working.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        // CI signs with a persistent key when the OMARG_KEYSTORE_* secrets are set, so updates install over each other.
        val keystore = System.getenv("OMARG_KEYSTORE_FILE")
        if (keystore != null && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("OMARG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OMARG_KEY_ALIAS")
                keyPassword = System.getenv("OMARG_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// The sherpa-onnx neural TTS runtime ships as a prebuilt AAR on GitHub releases (not on Maven).
val sherpaVersion = "1.13.8"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar").asFile

val downloadSherpa by tasks.registering {
    description = "Downloads the sherpa-onnx AAR if it is missing"
    outputs.file(sherpaAar)
    doLast {
        if (!sherpaAar.exists()) {
            sherpaAar.parentFile.mkdirs()
            val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
            logger.lifecycle("Downloading $url")
            val tmp = File(sherpaAar.path + ".part")
            URI(url).toURL().openStream().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            tmp.renameTo(sherpaAar)
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadSherpa) }

dependencies {
    implementation(files(sherpaAar))
    implementation("org.apache.commons:commons-compress:1.27.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
