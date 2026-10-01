import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing-Zugangsdaten aus ungetrackten Dateien (nicht im Git): je Variante eine eigene Datei/Keystore.
fun loadProps(name: String) = Properties().apply {
    val f = rootProject.file(name)
    if (f.exists()) f.inputStream().use { load(it) }
}
// Die Variante "pro" (Server-Abgleich) existiert nur, wenn src/pro vorhanden ist. Ohne diesen Ordner
// (oeffentliches Repo "inventur") baut das Projekt nur die Variante "classic".
val hasPro = file("src/pro").exists()
val keystorePropsPro = loadProps("keystore.properties")
// Im Pro-Repo liegt der Schluessel der alten App in keystore-classic.properties, im oeffentlichen Repo in keystore.properties.
val keystorePropsClassic = loadProps(if (hasPro) "keystore-classic.properties" else "keystore.properties")

android {
    namespace = "com.kenfenheuer.inventur"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Das Inateck-SDK (scanner_cmd) und JNA liefern native Bibliotheken nur fuer arm64-v8a.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Zwei Varianten aus demselben Kern: "classic" (Inventur, nur lokale Liste) und "pro" (mit Server-Abgleich).
    flavorDimensions += "edition"
    productFlavors {
        create("classic") {
            dimension = "edition"
            applicationId = "com.kenfenheuer.inventur"
            versionCode = 18
            versionName = "2.4"
            resValue("string", "app_name", "Inventur")
        }
        if (hasPro) {
            create("pro") {
                dimension = "edition"
                applicationId = "com.kenfenheuer.inventurpro"
                versionCode = 8
                versionName = "2.4"
                resValue("string", "app_name", "Inventur Pro")
            }
        }
    }

    signingConfigs {
        if (hasPro && keystorePropsPro.isNotEmpty()) {
            create("releasePro") {
                storeFile = file(keystorePropsPro.getProperty("storeFile"))
                storePassword = keystorePropsPro.getProperty("storePassword")
                keyAlias = keystorePropsPro.getProperty("keyAlias")
                keyPassword = keystorePropsPro.getProperty("keyPassword")
            }
        }
        if (keystorePropsClassic.isNotEmpty()) {
            create("releaseClassic") {
                storeFile = file(keystorePropsClassic.getProperty("storeFile"))
                storePassword = keystorePropsClassic.getProperty("storePassword")
                keyAlias = keystorePropsClassic.getProperty("keyAlias")
                keyPassword = keystorePropsClassic.getProperty("keyPassword")
            }
        }
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
    buildFeatures {
        compose = true
        resValues = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/MANIFEST.MF",
            )
        }
        // JNA benoetigt die entpackte libjnidispatch.so auf der Platte.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Release-Signatur je Variante (Debug bleibt mit dem Debug-Schluessel signiert, damit Updates installierbar bleiben).
androidComponents {
    // Debug-Build der alten App parallel zur installierten (andere Signatur) testen koennen.
    onVariants(selector().withBuildType("debug")) { v ->
        if (v.flavorName == "classic") v.applicationId.set(v.applicationId.get() + ".debug")
    }
    onVariants(selector().withBuildType("release")) { v ->
        val name = if (v.flavorName == "pro") "releasePro" else "releaseClassic"
        android.signingConfigs.findByName(name)?.let { v.signingConfig.setConfig(it) }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Inateck Scanner SDK (BLE) + Abhaengigkeiten
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.fastble)
    implementation(libs.gson)
    // JNA fuer das SDK; @aar bringt 16-KB-Page-Size-kompatible native Libs mit (s. o.).
    // jna-platform zieht transitiv das normale jna-Jar - das dupliziert die Klassen aus
    // dem @aar oben, deshalb hier ausgeschlossen.
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    implementation(libs.jna.platform) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }

    // Kamera-basierter Barcode-Scan (ZXing)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}