// Runs app/src/test/java/top/rootu/ssh/tunnel/vpn/** on a plain JVM. Only the Android-free network core is
// compiled: vpn/net/*, Socks5ProxyServer.kt and TunnelTransport.kt.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { kotlin("jvm") version "2.2.10" }

val vpn = rootDir.resolve("../../../app/src/main/java/top/rootu/ssh/tunnel/vpn").canonicalFile
val appTests = rootDir.resolve("../../../app/src/test/java/top/rootu/ssh/tunnel/vpn").canonicalFile

sourceSets["main"].kotlin.setSrcDirs(listOf(vpn))
sourceSets["main"].kotlin.setIncludes(listOf("net/*.kt", "Socks5ProxyServer.kt", "TunnelTransport.kt"))
sourceSets["test"].kotlin.setSrcDirs(listOf(appTests))

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }

dependencies { testImplementation("junit:junit:4.13.2") }

tasks.test {
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
