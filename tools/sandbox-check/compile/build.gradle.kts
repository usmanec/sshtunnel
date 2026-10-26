// Compiles app/src/main against:
//  - Robolectric android-all (android.* framework classes, Maven Central),
//  - JetBrains Compose Multiplatform desktop 1.7 (same API as androidx Compose 1.7 / Material3 1.3),
//  - small stubs for Android-only androidx APIs (stubskt/) and an R class generated from app/src/main/res.
// Only catches compile errors. Stubs mirror the signatures the app uses; update them when new
// Android-only APIs are used.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

val appSrc = rootDir.resolve("../../../app/src/main/java").canonicalPath
val appRes = rootDir.resolve("../../../app/src/main/res").canonicalFile
val generatedR = layout.buildDirectory.dir("generated/r")

// R.java with an id for every string, string-array, drawable, mipmap and font of the app
val generateR by tasks.registering {
    inputs.dir(appRes)
    outputs.dir(generatedR)
    doLast {
        val types = sortedMapOf<String, MutableSet<String>>()
        fun add(type: String, name: String) = types.getOrPut(type) { sortedSetOf() }.add(name)
        appRes.listFiles().orEmpty().forEach { dir ->
            val type = dir.name.substringBefore('-')
            if (type == "values") {
                dir.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
                    Regex("<(string|string-array|color)\\s+name=\"([A-Za-z0-9_]+)\"").findAll(file.readText()).forEach {
                        add(if (it.groupValues[1] == "string-array") "array" else it.groupValues[1], it.groupValues[2])
                    }
                }
            } else {
                dir.listFiles().orEmpty().forEach { add(type, it.nameWithoutExtension.substringBefore('.')) }
            }
        }
        var id = 0x7f000001
        val body = types.entries.joinToString("\n") { (type, names) ->
            "  public static final class $type {\n" +
                names.joinToString("\n") { "    public static final int $it = ${id++};" } + "\n  }"
        }
        val out = generatedR.get().file("top/rootu/ssh/tunnel/R.java").asFile
        out.parentFile.mkdirs()
        out.writeText("package top.rootu.ssh.tunnel;\n\npublic final class R {\n$body\n}\n")
    }
}

sourceSets["main"].java.srcDirs(appSrc, generatedR)
sourceSets["main"].kotlin.srcDirs(appSrc, "stubskt")
tasks.named("compileKotlin") { dependsOn(generateR) }
tasks.named("compileJava") { dependsOn(generateR) }

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
