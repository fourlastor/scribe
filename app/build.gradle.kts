plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/** One file of a speech model, and the asset path the app loads it from. */
data class ModelFile(val artifact: String, val extension: String, val asset: String, val sha256: String)

/**
 * A speech model hosted on Hugging Face under [owner], pinned to a commit.
 * [family] selects the engine implementation, see SpeechModels.kt.
 */
data class SpeechModel(
    val family: String,
    val repository: String,
    val revision: String,
    val files: List<ModelFile>,
    val owner: String = "csukuangfj",
)

fun whisper(size: String, revision: String, encoderSha256: String, decoderSha256: String) = SpeechModel(
    family = "whisper",
    repository = "sherpa-onnx-whisper-$size",
    revision = revision,
    files = listOf(
        ModelFile("$size-encoder.int8", "onnx", "model/encoder.onnx", encoderSha256),
        ModelFile("$size-decoder.int8", "onnx", "model/decoder.onnx", decoderSha256),
        ModelFile(
            "$size-tokens", "txt", "model/tokens.txt",
            "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
        ),
    ),
)

// To bundle a different model, add it here and select it with the scribe.model Gradle property.
// A new family also needs an engine in SpeechModels.kt.
val speechModels = mapOf(
    // 100 MB. Poor accuracy, only useful for fast development builds.
    "whisper-tiny" to whisper(
        size = "tiny",
        revision = "65176e2deb88badc814a94058666cadccc29b61c",
        encoderSha256 = "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
        decoderSha256 = "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
    ),
    // 160 MB.
    "whisper-base" to whisper(
        size = "base",
        revision = "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
        encoderSha256 = "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
        decoderSha256 = "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
    ),
    // 375 MB.
    "whisper-small" to whisper(
        size = "small",
        revision = "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
        encoderSha256 = "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9",
        decoderSha256 = "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee",
    ),
    // 1 GB. Most accurate, and several times slower than small.
    "whisper-turbo" to whisper(
        size = "turbo",
        revision = "2ca6ff69fc878651b770880507669577ac41c2ff",
        encoderSha256 = "b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b",
        decoderSha256 = "20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547",
    ),
    // 670 MB. NVIDIA Parakeet TDT 0.6b v3: 25 European languages, which it tells apart by itself.
    // The language cannot be chosen, so the app shows no language picker with it.
    "parakeet-tdt-v3" to SpeechModel(
        family = "parakeet",
        repository = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        revision = "2bda32ec70b097a55adaa07d9a7173915b43cc78",
        files = listOf(
            ModelFile(
                "encoder.int8", "onnx", "model/encoder.onnx",
                "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247",
            ),
            ModelFile(
                "decoder.int8", "onnx", "model/decoder.onnx",
                "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e",
            ),
            ModelFile(
                "joiner.int8", "onnx", "model/joiner.onnx",
                "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3",
            ),
            ModelFile(
                "tokens", "txt", "model/tokens.txt",
                "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
            ),
        ),
    ),
    // 680 MB. NVIDIA Nemotron 3.5 ASR: 35 languages, detected automatically or chosen by the
    // user. A streaming model, exported here with its largest look-ahead (1.12 s) for accuracy.
    "nemotron-3.5" to SpeechModel(
        family = "nemotron",
        owner = "csukuangfj2",
        repository = "sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-1120ms-int8-2026-06-11",
        revision = "cba1c96ca5ef0e8393b50584ae153a79145dc492",
        files = listOf(
            ModelFile(
                "encoder.int8", "onnx", "model/encoder.onnx",
                "2fff2166acaa535bd969fb223c1f0783d71029f143cb298bc54c2afe85abf772",
            ),
            ModelFile(
                "decoder.int8", "onnx", "model/decoder.onnx",
                "19f9c98fc6d0a2c33a65a43b36fdb2e914c26c0aa9764be3aebc502a1e982fb0",
            ),
            ModelFile(
                "joiner.int8", "onnx", "model/joiner.onnx",
                "4101c7c679a0bc30483794b27a059e34e79232aa2068d78d51231a22c8b0d7ce",
            ),
            ModelFile(
                "tokens", "txt", "model/tokens.txt",
                "729cc103155bafa785f9cd45746cd41cabe97eab7182fc04d594129587958f8a",
            ),
        ),
    ),
)

val speechModelId = providers.gradleProperty("scribe.model").get()
val speechModel = speechModels[speechModelId]
    ?: error("Unknown scribe.model '$speechModelId'. Available: ${speechModels.keys.joinToString()}")

// Voice activity detector used to split long recordings at pauses. The release tag it is published
// under is mutable, so the checksum is what pins it.
val vadRevision = "asr-models"
val vadModel = ModelFile(
    "silero_vad", "onnx", "vad/silero_vad.onnx",
    "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
)

// The release workflow hands the signing key over through these variables. Without them, release
// builds are signed with the debug key so that they can still be installed from a local build.
val releaseKeystore = providers.environmentVariable("SCRIBE_KEYSTORE").orNull

android {
    namespace = "io.github.fourlastor.scribe"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fourlastor.scribe"
        minSdk = 33
        targetSdk = 37
        versionCode = providers.gradleProperty("scribe.versionCode").get().toInt()
        versionName = providers.gradleProperty("scribe.versionName").get()

        ndk {
            // arm64 for phones, x86_64 for the emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        buildConfigField("String", "SPEECH_MODEL_ID", "\"$speechModelId\"")
        buildConfigField("String", "SPEECH_MODEL_FAMILY", "\"${speechModel.family}\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                val password = providers.environmentVariable("SCRIBE_KEYSTORE_PASSWORD").get()
                storeFile = file(releaseKeystore)
                storePassword = password
                keyAlias = providers.environmentVariable("SCRIBE_KEY_ALIAS").get()
                // PKCS12 keystores, the only kind keytool makes today, use one password for both.
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Stored uncompressed so installs do not have to inflate hundreds of megabytes.
        noCompress += "onnx"
    }

    packaging {
        jniLibs {
            // The Kotlin API only loads the JNI library and ONNX Runtime.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

val speechAssets = configurations.create("speechAssets") {
    isCanBeConsumed = false
    isTransitive = false
}

val bundleSpeechModel = tasks.register<BundleSpeechModelTask>("bundleSpeechModel") {
    fun resolvedName(file: ModelFile, revision: String) = "${file.artifact}-$revision.${file.extension}"
    val files = speechModel.files.associateBy { resolvedName(it, speechModel.revision) } +
        (resolvedName(vadModel, vadRevision) to vadModel)

    artifacts.from(speechAssets)
    assetPaths.set(files.mapValues { it.value.asset })
    checksums.set(files.mapValues { it.value.sha256 })
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleSpeechModel, BundleSpeechModelTask::outputDir)
    }
}

dependencies {
    speechAssets("${speechModel.owner}:${speechModel.repository}:${speechModel.revision}") {
        speechModel.files.forEach { file ->
            artifact {
                name = file.artifact
                extension = file.extension
                type = file.extension
            }
        }
    }
    speechAssets("com.k2fsa.models:silero-vad:$vadRevision") {
        artifact {
            name = vadModel.artifact
            extension = vadModel.extension
            type = vadModel.extension
        }
    }

    implementation("${libs.sherpa.onnx.get()}@aar")

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
