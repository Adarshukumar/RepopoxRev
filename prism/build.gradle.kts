plugins {
    /* Declared here so the subproject inherits a single resolved version.
       kotlin.android is absent on purpose: AGP 9 supplies Kotlin itself and
       fails the build if the standalone plugin is applied. */
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
