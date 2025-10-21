
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}


android {
    namespace = "com.example.voicestorm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.voicestorm"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

}


dependencies {

    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))

    // Add the dependency for Firebase Analytics
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.gms.play.services.ads)
    implementation(libs.play.services.location)
    //implementation(libs.gms.play.services)
    //implementation(libs.speech)

    //implementation("com.google.android.gms:play-services-ads:24.1.0")
    //implementation("com.google.android.gms:play-services-auth:21.2.0")
    //implementation("com.google.android.gms:play-services-location:21.3.0")
    //implementation("com.google.android.gms:play-services-maps:18.2.0")
    //implementation("com.google.android.gms:play-services-drive:17.0.0")
    //implementation("com.google.android.gms:play-services-fitness:21.2.0")

    // --- DEPENDENCIA DE ML KIT SPEECH (Gratuita, vía Play Services) ---
    //implementation(libs.play.services.mlkit.speech.recognition)


    // --- DEPENDENCIA DE ML KIT SPEECH (Gratuita, vía Play Services) ---
    //implementation(libs.play.services.mlkit.speech.recognition)


    // --- DEPENDENCIAS DE ROOM ---
    //val room_version = "2.6.1"

    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)

    // Opcional - Soporte para Corrutinas en Room
    implementation(libs.androidx.room.ktx)
    // --- FIN DEPENDENCIAS DE ROOM ---

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}