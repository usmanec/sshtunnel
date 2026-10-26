// Runs app/src/test/java/top/rootu/ssh/tunnel/{vpn,update}/** on a plain JVM. Only the Android-free network core is
// compiled: vpn/net/*, Socks5ProxyServer.kt, TunnelTransport.kt and update/ReleaseInfo.kt.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { kotlin("jvm") version "2.2.10" }

val app = rootDir.resolve("../../../app/src/main/java/top/rootu/ssh/tunnel").canonicalFile
val appTests = rootDir.resolve("../../../app/src/test/java/top/rootu/ssh/tunnel").canonicalFile

// Android-free parts only: the network core and the updater's release logic
sourceSets["main"].kotlin.setSrcDirs(listOf(app.resolve("vpn"), app.resolve("update")))
sourceSets["main"].kotlin.setIncludes(listOf("net/*.kt", "Socks5ProxyServer.kt", "TunnelTransport.kt", "ReleaseInfo.kt"))
sourceSets["test"].kotlin.setSrcDirs(listOf(appTests.resolve("vpn"), appTests.resolve("update")))

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }

dependencies { testImplementation("junit:junit:4.13.2") }

tasks.test {
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
