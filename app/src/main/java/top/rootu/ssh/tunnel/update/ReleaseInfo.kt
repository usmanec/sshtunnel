package top.rootu.ssh.tunnel.update

/** A newer release found on GitHub. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val size: Long
)

/**
 * Pure logic of the updater (no Android): picking the APK from a GitHub release and comparing
 * versions. Kept separate so it is covered by plain JVM tests.
 */
object ReleaseInfo {
    private val VERSION_REGEX = Regex("""^\d+(\.\d+)*""")

    /**
     * Parses a GitHub "latest release" object. Returns null when the release is not newer than
     * [currentVersion], is a draft/pre-release or has no APK.
     */
    fun parse(release: Map<String, Any?>, currentVersion: String?): UpdateInfo? {
        if (release["draft"] == true || release["prerelease"] == true) return null
        val tag = release["tag_name"] as? String ?: return null
        if (currentVersion != null && !isNewer(tag, currentVersion)) return null

        @Suppress("UNCHECKED_CAST")
        val assets = (release["assets"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
        val apk = assets.firstOrNull { (it["name"] as? String)?.endsWith(".apk", ignoreCase = true) == true }
            ?: return null
        val url = apk["browser_download_url"] as? String ?: return null
        return UpdateInfo(
            version = tag,
            notes = (release["body"] as? String).orEmpty().trim(),
            downloadUrl = url,
            size = (apk["size"] as? Number)?.toLong() ?: 0L
        )
    }

    /**
     * Compares a release tag like "v1.2.3" with the installed version, which may carry
     * git describe suffixes ("1.2.3-5-gabcdef", "1.2.3-dirty", "0.0.1-dev-abcdef").
     * Only the numeric part counts, so a build made after a tag is not older than that tag
     * and an older release is never offered as an update.
     */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parseVersion(remote) ?: return false
        val c = parseVersion(current) ?: return true
        for (i in 0 until maxOf(r.size, c.size)) {
            val diff = r.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun parseVersion(version: String): List<Int>? {
        val match = VERSION_REGEX.find(version.trim().removePrefix("v").removePrefix("V")) ?: return null
        return match.value.split('.').map { it.toIntOrNull() ?: 0 }
    }
}
