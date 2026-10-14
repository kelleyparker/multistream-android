// The Android Gradle plugin is applied in :app only, so `-PcoreOnly` works without Google's Maven.
plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}
