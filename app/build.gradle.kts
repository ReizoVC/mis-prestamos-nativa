import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun leer(archivo: File): Properties = Properties().apply {
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

val nube = leer(rootProject.file("nube.properties"))
// La llave de firma vive fuera del proyecto (carpeta firma-android) y no se sube a GitHub.
val carpetaFirma = rootProject.file("../firma-android")
val almacen = File(carpetaFirma, "mis-prestamos.keystore")
val claveFirma = leer(File(carpetaFirma, "clave.env")).getProperty("MIS_PRESTAMOS_KEYSTORE_PASS")
val hayFirma = almacen.exists() && !claveFirma.isNullOrBlank()

// Actualizaciones: la app mira la última versión publicada en este repositorio de GitHub (debe ser público).
// -PpruebaActualizacion=http://... apunta a un servidor local, solo para probar el flujo.
val repoActualizaciones = (findProperty("actualizaciones.repo") as String?).orEmpty().trim()
val fuentePrueba = findProperty("pruebaActualizacion") as String?
val fuenteActualizaciones = fuentePrueba
    ?: if (repoActualizaciones.isNotEmpty()) "https://api.github.com/repos/$repoActualizaciones/releases/latest" else ""

android {
    namespace = "pe.reizo.prestamos"
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.reizo.prestamos"
        minSdk = 26
        targetSdk = 36
        // Para publicar: subir los dos. versionCode siempre aumenta de uno en uno.
        versionCode = (findProperty("vc") as String?)?.toInt() ?: 3
        versionName = (findProperty("vn") as String?) ?: "1.0.2"
        buildConfigField("String", "ACTUALIZACIONES_URL", "\"$fuenteActualizaciones\"")
        manifestPlaceholders["textoClaro"] = (fuentePrueba != null).toString()
        // MODO DESARROLLO: mientras sea true la app NO guarda nada. Los datos viven en memoria,
        // arrancan con los clientes de ejemplo y se pierden al cerrar. Tampoco se conecta a la nube.
        // Poner en false cuando se conecte la base de datos definitiva.
        buildConfigField("boolean", "MODO_DESARROLLO", "true")
        buildConfigField("String", "SUPABASE_URL", "\"${nube.getProperty("SUPABASE_URL", "").trim()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${nube.getProperty("SUPABASE_ANON_KEY", "").trim()}\"")
    }

    signingConfigs {
        if (hayFirma) create("propia") {
            storeFile = almacen
            storePassword = claveFirma
            keyAlias = "mis-prestamos"
            keyPassword = claveFirma
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (hayFirma) "propia" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
