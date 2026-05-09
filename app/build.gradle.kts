plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.das.miau"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.das.miau"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.preference:preference:1.2.1")
    implementation(libs.play.services.location)

    // Librería para WorkManager (conexión en segundo plano)
    implementation("androidx.work:work-runtime:2.9.0")

    // Librería JSON con la exclusión para evitar clases duplicadas
    implementation("com.googlecode.json-simple:json-simple:1.1.1") {
        exclude(group = "junit", module = "junit")
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}