import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.dawidkrynski.mediagrab"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.dawidkrynski.mediagrab"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"
        ndk { abiFilters += (project.findProperty("abi") as String? ?: "arm64-v8a") }
    }
    // The release key lives outside the repository: a properties file with storeFile, storePassword, keyAlias, keyPassword
    val signingFile = file((findProperty("signingProps") as String?) ?: "${System.getProperty("user.home")}/.config/niutics-android/mediagrab/signing.properties")
    val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }
    signingConfigs {
        if (signingFile.exists()) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "zip" }
}

// gallery-dl ships as Python sources in third_party/python; the APK carries them as one zip on PYTHONPATH.
val galleryDlAssets = layout.buildDirectory.dir("generated/gallery-dl-assets")
val zipGalleryDl by tasks.registering(Zip::class) {
    from(rootProject.file("third_party/python")) { exclude("**/__pycache__/**") }
    archiveFileName.set("gallery_dl.zip")
    destinationDirectory.set(galleryDlAssets)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
android.sourceSets["main"].assets.srcDir(galleryDlAssets)
tasks.named("preBuild") { dependsOn(zipGalleryDl) }

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    testImplementation("junit:junit:4.13.2")
}
