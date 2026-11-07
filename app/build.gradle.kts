import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val requiredReleaseProperties = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
)
val releaseSigningProperties = requiredReleaseProperties.associateWith { property ->
    keystoreProperties.getProperty(property)?.takeIf(String::isNotBlank)
}
val hasReleaseSigning = releaseSigningProperties.values.all { it != null }

android {
    namespace = "com.duquxian.toolbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.duquxian.toolbox"
        minSdk = 28
        targetSdk = 35
        versionCode = 18
        versionName = "0.9.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getValue("storeFile")!!)
                storePassword = releaseSigningProperties.getValue("storePassword")
                keyAlias = releaseSigningProperties.getValue("keyAlias")
                keyPassword = releaseSigningProperties.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}

