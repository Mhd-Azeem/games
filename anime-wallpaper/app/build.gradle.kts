import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.az.animewallpaper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.az.animewallpaper"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-dev"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

android {
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    signingConfigs {
        create("production") {
            val signingPath = System.getenv("AZ_SIGNING_STORE")
            if (!signingPath.isNullOrBlank()) {
                storeFile = file(signingPath)
                storePassword = System.getenv("AZ_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("AZ_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("AZ_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".dev" }
        getByName("release") {
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Bundle a pinned model in every APK; no model downloads occur on the phone.
val spatialModel = layout.projectDirectory.file("src/main/assets/spatial_subject.tflite").asFile
val prepareSpatialModel by
    tasks.registering {
        outputs.file(spatialModel)
        doLast {
            if (!spatialModel.exists()) {
                spatialModel.parentFile.mkdirs()
                val temp = File(spatialModel.parentFile, "spatial.tmp")
                try {
                    URI(
                            "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite"
                        )
                        .toURL()
                        .openStream()
                        .use { input -> temp.outputStream().use { input.copyTo(it) } }
                    check(temp.renameTo(spatialModel))
                } finally {
                    temp.delete()
                }
            }
            val hash =
                MessageDigest.getInstance("SHA-256").digest(spatialModel.readBytes()).joinToString(
                    ""
                ) {
                    "%02x".format(it)
                }
            check(hash == "ff36e24d40547fe9e645e2f4e8745d1876d6e38b332d39a82f0bf0f5d1d561b3") {
                "Spatial model checksum mismatch"
            }
        }
    }

tasks.named("preBuild") { dependsOn(prepareSpatialModel) }
