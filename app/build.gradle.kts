import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun gitVersionName(): String {
    fun exec(vararg cmd: String) = providers.exec {
        commandLine(*cmd)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
    return try {
        val tag = exec("git", "describe", "--tags", "--abbrev=0").ifBlank { return "dev" }
        val sha = exec("git", "rev-parse", "--short", "HEAD").ifBlank { return "dev" }
        "$tag-$sha"
    } catch (_: Exception) { "dev" }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "app.mmmap"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mmmap"
        minSdk = 26
        targetSdk = 35
        versionCode = (findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = (findProperty("versionName") as? String) ?: gitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    lint {
        disable += "NullSafeMutableLiveData"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Prevent aapt2 from compressing the bundled SQLite asset.
        // Android's AssetManager can't stream compressed assets >1 MB, which
        // would cause Room's createFromAsset to throw on first launch.
        noCompress += "db"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
        // Store .so files uncompressed with 16KB-aligned offsets (Android 15+)
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

kotlin {
    // Set the Kotlin compile target to 17 *without* requesting a toolchain.
    // jvmToolchain(17) would force Gradle to locate/provision a JDK 17, which
    // fails on F-Droid's build server (auto-provisioning disabled). Using
    // compilerOptions lets Kotlin compile to 17 bytecode with whatever JDK
    // is running Gradle — JDK 17 is required by compileOptions above anyway.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // MapLibre
    implementation(libs.maplibre)

    // Coroutines
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
