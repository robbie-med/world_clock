import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ks = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.robbiemed.zones"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.robbiemed.zones"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }
    signingConfigs {
        if (ks.containsKey("storeFile")) create("release") {
            storeFile = rootProject.file(ks.getProperty("storeFile"))
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias")
            keyPassword = ks.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging {
        resources.excludes += setOf("kotlin/**", "META-INF/*.version", "META-INF/**.kotlin_module", "DebugProbesKt.bin", "META-INF/LICENSE*")
    }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(project(":core"))
}
