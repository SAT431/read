plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.example.read"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.example.read"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").assets.srcDir("../api-contracts/prompts")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:catalog"))
    implementation(project(":feature:library"))
    implementation(project(":core:runtime"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":data:local"))
    implementation(project(":data:remote"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:study"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:explorer"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
