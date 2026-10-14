plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "com.kelleyparker.multistream"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.kelleyparker.multistream"
    minSdk = 26
    targetSdk = 37
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
    versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
  }

  // Release signing comes from CI secrets; without them the release build is left unsigned.
  val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
  signingConfigs {
    if (releaseKeystore != null) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      signingConfigs.findByName("release")?.let { signingConfig = it }
      // R8 stays off until shrinking is checked on a real device with RootEncoder.
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  lint {
    abortOnError = true
    // Version bumps are handled deliberately, not by lint.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
  }
}

dependencies {
  implementation(project(":core"))

  implementation(libs.rootencoder.library)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.coroutines.android)
  debugImplementation(libs.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
}
