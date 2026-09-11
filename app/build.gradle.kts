plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.weather.hforecast"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.weather.hforecast"
        minSdk = 23
        targetSdk = 36
        versionCode = 12
        versionName = "0.5.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

android.applicationVariants.all {
    val appVersion = versionName
    outputs.all {
        (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
            "HForecast_$appVersion.apk"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":weather-core"))
    implementation(project(":weather-ensemble"))
    implementation(project(":weather-verification"))
    implementation(project(":weather-providers"))
    implementation(project(":database"))
    implementation(project(":location"))
    implementation(project(":ui"))
    implementation(project(":settings"))
    implementation(project(":environment"))
    implementation(project(":astronomy"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
