pluginManagement {
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
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    maven("https://jitpack.io") {
      content { includeGroupByRegex("com\\.github\\.pedroSG94.*") }
    }
  }
}

rootProject.name = "multistream-android"

include(":core")
// `-PcoreOnly` builds and tests the platform rules without the Android SDK.
if (!providers.gradleProperty("coreOnly").isPresent) {
  include(":app")
}
