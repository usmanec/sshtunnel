// Compile check of all app sources without the Android SDK (see docs/TESTING.md).
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "sandbox-compile-check"
