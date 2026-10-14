// All Gradle plugins live on one root classpath so the Android and Kotlin plugins can see each
// other. `-PcoreOnly` leaves the Android-only plugins out, so :core builds without the Android SDK.
buildscript {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath(libs.plugin.kotlin)
    classpath(libs.plugin.kotlin.serialization)
    if (!providers.gradleProperty("coreOnly").isPresent) {
      classpath(libs.plugin.android)
      classpath(libs.plugin.kotlin.compose)
      classpath(libs.plugin.roborazzi)
    }
  }
}
