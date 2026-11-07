import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.prism.analysis"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.prism.analysis"
        /* 26 keeps the launcher icon pure XML (adaptive icons arrive at 26, so no
           legacy PNG mipmaps are needed) and gives Compose a modern enough WebView-
           free canvas to animate on without jank workarounds. */
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            /* the artifact CI publishes; signed with the standard debug key so it
               installs like any other APK */
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            /* no signingConfig: a release build here would be unsigned. CI builds
               debug unless a key is provided. */
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        /* BuildConfig is off by default under AGP 8+; nothing here needs it */
        buildConfig = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    /* Compose only. No Hilt, no Room, no navigation library, no network stack: the
       analysis is pure functions over a string, so there is nothing to inject,
       persist or fetch. Every dependency not declared here is one fewer thing that
       can fail to resolve in a build nobody could compile locally. */
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.animation)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.splashscreen)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)

    /* the analysis engine is pure Kotlin with no Android imports, so it is tested on
       the JVM. CI runs these: it is the only way to actually prove the metrics are
       correct from an environment that cannot execute anything itself. */
    testImplementation(libs.junit)
}
