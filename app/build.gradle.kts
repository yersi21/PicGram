import java.util.Properties

import java.util.Base64

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val supabaseUrl = localProperties.getProperty("SUPABASE_URL")?.trim().orEmpty()
val supabaseKey = localProperties.getProperty("SUPABASE_KEY")?.trim().orEmpty()
require(supabaseUrl.startsWith("https://") && supabaseUrl.none { it == '"' || it == '\n' || it == '\r' }) {
    "Configura SUPABASE_URL (HTTPS) en local.properties"
}
require(supabaseKey.isNotBlank() && supabaseKey.none { it == '"' || it == '\\' || it.isWhitespace() }) {
    "Configura SUPABASE_KEY con la clave anon/publishable en local.properties"
}
val keyPayload = runCatching {
    String(Base64.getUrlDecoder().decode(supabaseKey.split('.')[1]))
}.getOrDefault("")
require(!supabaseKey.startsWith("sb_secret_") && !keyPayload.contains("service_role")) {
    "Una app Android debe usar una clave anon/publishable, nunca una clave secreta"
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.picgram"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.picgram"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"


        // La clave publica se incluye en el APK; la seguridad depende de RLS.
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.trimEnd('/')}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Coroutines para operaciones IO (subida a Supabase, llamadas REST)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Iconos de Material Design para Compose
    implementation("androidx.compose.material:material-icons-extended")

    // Coil para carga de imagenes desde URLs de Supabase Storage
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Reproductor adaptable, con controles integrados y soporte de video rotado.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
