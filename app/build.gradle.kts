plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
// Temporary side-by-side build requested by the user. Use -PatmytrackV2=false for the original package.
val temporaryV2 = providers.gradleProperty("atmytrackV2").map { it.toBoolean() }.orElse(true).get()
android {
    namespace = "com.atmytrack.app"
    compileSdk = 36
    defaultConfig {
        applicationId = if (temporaryV2) "com.atmytrack.app.v2" else "com.atmytrack.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "0.17.0"
        manifestPlaceholders["appLabel"] = if (temporaryV2) "ATMyTrack_v2" else "ATMyTrack"
        resValue("string", "app_name", if (temporaryV2) "ATMyTrack_v2" else "ATMyTrack")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; resValues = true }
    ndkVersion = "28.2.13676358"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
tasks.register<Copy>("exportDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")) { rename { "ATMyTrack-debug.apk" } }
    if (temporaryV2) {
        from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")) { rename { "ATMyTrack_v2.apk" } }
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
