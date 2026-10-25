// Compiles app/src/main against:
//  - Robolectric android-all (android.* framework classes, Maven Central),
//  - JetBrains Compose Multiplatform desktop 1.7 (same API as androidx Compose 1.7 / Material3 1.3),
//  - small stubs for Android-only androidx APIs (stubskt/, stubs-java/).
// Only catches compile errors. Stubs mirror the signatures the app uses; update them when new
// Android-only APIs are used.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

val appSrc = rootDir.resolve("../../../app/src/main/java").canonicalPath
sourceSets["main"].java.srcDirs(appSrc, "stubs-java")
sourceSets["main"].kotlin.srcDirs(appSrc, "stubskt")

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }

dependencies {
    compileOnly("org.robolectric:android-all:14-robolectric-10818077")
    implementation("org.jetbrains.compose.material3:material3-desktop:1.7.0")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.0")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:1.7.0")
    implementation("org.jetbrains.compose.ui:ui-desktop:1.7.0")
    implementation("com.github.mwiede:jsch:0.2.21")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
