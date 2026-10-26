package top.rootu.ssh.tunnel.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTypeTest {
    @Test
    fun `key is always tried before the password`() {
        for (type in AuthType.values()) {
            val methods = type.sshMethods.split(',')
            assertEquals(type.usesKey, "publickey" in methods)
            assertEquals(type.usesPassword, "password" in methods)
            if (type.usesKey && type.usesPassword) {
                assertTrue(methods.indexOf("publickey") < methods.indexOf("password"))
            }
        }
    }

    @Test
    fun `stored names stay stable`() {
        // Room stores the enum name; renaming a value would break saved servers
        assertEquals(
            listOf("PASSWORD", "PRIVATE_KEY", "PRIVATE_KEY_AND_PASSWORD"),
            AuthType.values().map { it.name }
        )
    }
}
