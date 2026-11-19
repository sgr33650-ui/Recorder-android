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
        versionCode = 4
        versionName = "1.3"
    }

    // Clé fixe : chaque nouvelle version peut s'installer par-dessus la précédente.
    signingConfigs {
        create("perso") {
            storeFile = file("signature.jks")
            storePassword = "enregistreur"
            keyAlias = "enregistreur"
            keyPassword = "enregistreur"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    flavorDimensions += "variante"
    productFlavors {
        create("complet") {
            dimension = "variante"
            resValue("string", "app_name", "Appels Rec")
        }
        // Version de test sans aucune permission sensible
        create("essai") {
            dimension = "variante"
            applicationIdSuffix = ".essai"
            resValue("string", "app_name", "Test install")
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
