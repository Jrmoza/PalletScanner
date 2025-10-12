plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.despacho.palletscanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.despacho.palletscanner"
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
        compose = true
    }
}

dependencies {

    // Básicas de Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
// Interfaz de usuario (Compose)
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
// Navegación entre pantallas
    implementation("androidx.navigation:navigation-compose:2.7.5")
// Manejo de estados
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
// SignalR (para comunicarse con tu servidor)
    implementation("com.microsoft.signalr:signalr:8.0.0")
// JSON (para manejar datos)
    implementation("com.google.code.gson:gson:2.10.1")
// Corrutinas (para operaciones asíncronas)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
// Almacenamiento de configuraciones
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
// ML Kit para reconocimiento de códigos de barras
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
}