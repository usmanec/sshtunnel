package top.rootu.ssh.tunnel.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthType {
    PASSWORD,
    PRIVATE_KEY
}

@Entity(tableName = "ssh_servers")
data class SshServerProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val socksPort: Int = 10808,
    val compression: Boolean = false,
    val keepAliveIntervalSeconds: Int = 15,
    val isActive: Boolean = false,
    val lastPingMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
