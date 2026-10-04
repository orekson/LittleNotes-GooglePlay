plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "tw.local.memonote"
    compileSdk = 36
    defaultConfig {
        applicationId = "tw.local.memonote"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.5.0"
        testInstrumentationRunner = "tw.local.memonote.DemoSetup"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { disable += "OldTargetApi" }
}
dependencies { implementation("com.google.android.gms:play-services-auth:22.0.0"); testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303"); testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0"); androidTestImplementation("androidx.test:runner:1.6.2"); androidTestImplementation("androidx.test.ext:junit:1.2.1") }
