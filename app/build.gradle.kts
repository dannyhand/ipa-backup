import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from environment variables (CI) or keystore.properties (local, gitignored).
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: signingProps.getProperty(prop)
val releaseStoreFile = signingValue("KEYSTORE_FILE", "storeFile")

android {
    namespace = "us.dannyhand.ipabackup"
    compileSdk = 35

    defaultConfig {
        applicationId = "us.dannyhand.ipabackup"
        minSdk = 24
        targetSdk = 35
        versionCode = 14
        versionName = "1.4"
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // R8 is off: BouncyCastle uses reflection and the APK is small anyway.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val bc = "1.79"
    implementation("org.bouncycastle:bcprov-jdk18on:$bc")
    implementation("org.bouncycastle:bcpkix-jdk18on:$bc")
    implementation("org.bouncycastle:bctls-jdk18on:$bc")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}
