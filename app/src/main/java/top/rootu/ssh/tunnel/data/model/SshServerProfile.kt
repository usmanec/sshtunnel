package top.rootu.ssh.tunnel.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Stored by name in Room (see Converters): never rename existing values. */
enum class AuthType(
    val usesKey: Boolean,
    val usesPassword: Boolean,
    /** JSch "PreferredAuthentications": the key goes first, then the password. */
    val sshMethods: String
) {
    PASSWORD(usesKey = false, usesPassword = true, sshMethods = "password,keyboard-interactive"),
    PRIVATE_KEY(usesKey = true, usesPassword = false, sshMethods = "publickey"),

    /**
     * Key, then password: works both as a fallback (key not accepted, password is) and for
     * servers that require both (OpenSSH "AuthenticationMethods publickey,password").
     */
    PRIVATE_KEY_AND_PASSWORD(usesKey = true, usesPassword = true, sshMethods = "publickey,password,keyboard-interactive")
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
