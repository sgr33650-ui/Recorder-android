plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.perso.enregistreur"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.perso.recorder"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Clé fixe : chaque nouvelle version peut s'installer par-dessus la précédente.
    signingConfigs {
        create("perso") {
            storeFile = file("signature.jks")
            storePassword = "enregistreur"
            keyAlias = "enregistreur"
            keyPassword = "enregistreur"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("perso")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("perso")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
