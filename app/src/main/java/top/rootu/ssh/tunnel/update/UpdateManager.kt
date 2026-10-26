package top.rootu.ssh.tunnel.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import top.rootu.ssh.tunnel.vpn.net.MiniJson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates from GitHub releases: check the latest release, download its APK, verify it
 * and hand it to the system installer. The app is excluded from the tunnel, so GitHub is reached
 * over the device's own connection.
 */
class UpdateManager(private val context: Context) {

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/usmanec/sshtunnel/releases/latest"
        private const val APK_NAME = "update.apk"
        private const val TIMEOUT_MS = 15_000
    }

    enum class Problem {
        /** The file is not an APK of this app. */
        NOT_THIS_APP,
        /** Signed with another key: Android would refuse to install it over this app. */
        SIGNATURE_MISMATCH
    }

    class InvalidApkException(val problem: Problem) : IOException(problem.name)

    val currentVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    /** The newer release, or null when the installed version is up to date. Throws on network errors. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = open(LATEST_RELEASE_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> null // no releases published yet
                in 200..299 -> {
                    val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    ReleaseInfo.parse(MiniJson.parseObject(body), currentVersion)
                }
                else -> throw IOException("HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads and verifies the APK. [onProgress] gets 0..100 (or -1 when the size is unknown). */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val file = updateFile()
        file.delete()
        try {
            val connection = open(info.downloadUrl)
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.size
                connection.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastProgress = -2
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            val progress = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
                if (total > 0 && file.length() != total) {
                    throw IOException("Incomplete download: ${file.length()} of $total bytes")
                }
            } finally {
                connection.disconnect()
            }
            verify(file)
            file
        } catch (e: Exception) {
            // Never leave a broken or foreign file behind
            file.delete()
            throw e
        }
    }

    /** Android 8+ asks the user once to allow installs from this app. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" setting for this app. False when the device has no such screen. */
    fun openInstallPermissionSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Starts the system installer. False when no installer activity exists. */
    fun install(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun deleteDownloadedUpdate() {
        updateFile().delete()
    }

    private fun updateFile(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.cacheDir, "updates")
        dir.mkdirs()
        return File(dir, APK_NAME)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // Release assets redirect to another https host; HttpURLConnection follows that
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SSH-Tunnel/$currentVersion")
        }

    /** Same package and same signing key as the installed app, otherwise the installer would fail. */
    private fun verify(file: File) {
        val pm = context.packageManager
        val archive = archiveInfo(pm, file.absolutePath) ?: throw InvalidApkException(Problem.NOT_THIS_APP)
        if (archive.packageName != context.packageName) throw InvalidApkException(Problem.NOT_THIS_APP)
        val installed = installedInfo(pm)
        val ours = signatureDigests(installed)
        val theirs = signatureDigests(archive)
        if (ours.isNotEmpty() && theirs.isNotEmpty() && ours.intersect(theirs).isEmpty()) {
            throw InvalidApkException(Problem.SIGNATURE_MISMATCH)
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }

    @Suppress("DEPRECATION")
    private fun signatureDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        val sha = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}
