package com.sshtunnel.data.model

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    IDLE_SUSPENDED,
    ERROR
}

data class VpnStats(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val speedInBps: Long = 0L,
    val speedOutBps: Long = 0L,
    val pingMs: Long? = null,
    val connectedDurationSeconds: Long = 0L,
    val activeConnections: Int = 0,
    val isIdleSuspended: Boolean = false
) {
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bps: Long): String {
        return when {
            bps >= 1024 * 1024 -> String.format("%.2f MB/s", bps.toDouble() / (1024 * 1024))
            bps >= 1024 -> String.format("%.1f KB/s", bps.toDouble() / 1024)
            else -> "$bps B/s"
        }
    }
}

data class ConnectionLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}
