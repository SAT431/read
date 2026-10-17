plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.folio.designsystem"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.material3)
    api(libs.compose.foundation)
    api(libs.compose.ui)
}
