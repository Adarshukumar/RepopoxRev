import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}
android {
    namespace = "dev.frame.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.frame.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UBOL_ID", "\"" + rootProject.file("ubol-id.txt").readText().trim() + "\"")
        buildConfigField("String", "UBOL_VERSION", "\"2026.1103.1743\"")
    }
    buildTypes { debug { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    buildFeatures { compose = true; buildConfig = true }
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = false } }
    // AAPT's default underscore-directory exclusion strips WebExtension _locales.
    // Preserve the upstream package byte-for-byte, including those directories.
    androidResources { ignoreAssetsPattern = "!.git:!.svn:!*.scc:!CVS:!*~" }
    testOptions { animationsDisabled = true }
}
dependencies {
    implementation("org.mozilla.geckoview:geckoview:" + rootProject.file("geckoview-version.txt").readText().trim())
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
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.2")
    testImplementation(libs.junit)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
