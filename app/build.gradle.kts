// Version from git, as in dddplayer: versionCode = number of commits, versionName = nearest tag
// ("v1.2.3" -> "1.2.3", builds after a tag look like "1.2.3-4-gabcdef"). The in-app updater
// compares it with the latest GitHub release tag, so releases must be tagged vX.Y.Z.
// Needs the full history (fetch-depth: 0 in CI), a shallow clone gives a wrong versionCode.
fun gitOutput(vararg command: String): String = try {
  providers.exec {
    commandLine(*command)
    isIgnoreExitValue = true
  }.standardOutput.asText.get().trim()
} catch (e: Exception) {
  ""
}

val gitVersionCode: Int by lazy { gitOutput("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1 }

val gitVersionName: String by lazy {
  val described = gitOutput("git", "describe", "--tags", "--dirty")
  if (described.isEmpty() || described.contains("fatal")) {
    // No tags yet: a development version that any real release is newer than
    val hash = gitOutput("git", "rev-parse", "--short", "HEAD")
    if (hash.isNotEmpty()) "0.0.1-dev-$hash" else "0.0.1"
  } else {
    described.removePrefix("v")
  }
}

// Release signing for GitHub releases (see .github/workflows/release.yml). KEYSTORE_FILE is
// relative to the app module. Without these variables release builds stay unsigned.
fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseKeystorePath: String? = envOrNull("KEYSTORE_FILE")

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "top.rootu.ssh.tunnel"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "top.rootu.ssh.tunnel"
    minSdk = 24
    targetSdk = 36
    versionCode = gitVersionCode
    versionName = gitVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  if (releaseKeystorePath != null) {
    signingConfigs {
      create("github") {
        storeFile = file(releaseKeystorePath)
        storePassword = envOrNull("KEYSTORE_PASSWORD")
        keyAlias = envOrNull("RELEASE_SIGN_KEY_ALIAS")
        keyPassword = envOrNull("RELEASE_SIGN_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      if (releaseKeystorePath != null) {
        // Every release must be signed with the same key, otherwise in-app updates fail
        signingConfig = signingConfigs.getByName("github")
      }
      isCrunchPngs = false
      // R8 removes unused code (most of material-icons-extended, Compose, BouncyCastle);
      // keep rules for JSch reflection are in proguard-rules.pro
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      // signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      // signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      // JSch and BouncyCastle are multi-release jars with identical OSGi manifests
      excludes += "/META-INF/versions/*/OSGI-INF/MANIFEST.MF"
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  // Includes material-icons-core; unused icons are stripped by R8 in release builds
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.mwiede.jsch)
  // Ed25519 keys and curve25519 key exchange in JSch on Android
  implementation(libs.bouncycastle.bcprov)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.androidx.core)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  "ksp"(libs.androidx.room.compiler)
}
