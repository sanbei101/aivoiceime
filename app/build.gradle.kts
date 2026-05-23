import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun localProperty(name: String): String? {
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.isFile) return null
    return Properties().apply {
        localProperties.inputStream().use(::load)
    }.getProperty(name)
}

fun secretProperty(name: String): String {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperty(name)
        ?: ""
}

fun buildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "cn.sanbei101.aivoiceime"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.sanbei101.aivoiceime"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "ASR_API_KEY", buildConfigString(secretProperty("ASR_API_KEY")))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
