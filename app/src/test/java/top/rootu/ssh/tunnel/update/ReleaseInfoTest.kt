package top.rootu.ssh.tunnel.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.rootu.ssh.tunnel.vpn.net.MiniJson

class ReleaseInfoTest {
    private val release = MiniJson.parseObject(
        """
        {"tag_name":"v1.3.0","draft":false,"prerelease":false,"body":"## Changes\n- fix",
         "assets":[
           {"name":"checksums.txt","browser_download_url":"https://example.com/sum","size":10},
           {"name":"app-release.apk","browser_download_url":"https://example.com/app.apk","size":12345678}
         ]}
        """
    )

    @Test
    fun `newer release with an apk is offered`() {
        val info = ReleaseInfo.parse(release, "1.2.9-4-gabcdef")!!
        assertEquals("v1.3.0", info.version)
        assertEquals("https://example.com/app.apk", info.downloadUrl)
        assertEquals(12345678L, info.size)
        assertEquals("## Changes\n- fix", info.notes)
    }

    @Test
    fun `same or older version, drafts and releases without apk are ignored`() {
        assertNull(ReleaseInfo.parse(release, "1.3.0"))
        assertNull(ReleaseInfo.parse(release, "1.3.0-2-g1234567"))
        assertNull(ReleaseInfo.parse(release, "2.0"))
        assertNull(ReleaseInfo.parse(release + ("prerelease" to true), "1.0"))
        assertNull(ReleaseInfo.parse(release + ("assets" to emptyList<Any>()), "1.0"))
    }

    @Test
    fun `version comparison`() {
        assertTrue(ReleaseInfo.isNewer("v1.2.10", "1.2.9"))
        assertTrue(ReleaseInfo.isNewer("v0.1.0", "0.0.1-dev-abcdef"))
        assertTrue(ReleaseInfo.isNewer("v1.0.1", "1.0"))
        assertFalse(ReleaseInfo.isNewer("v1.0", "1.0.0"))
        assertFalse(ReleaseInfo.isNewer("v1.2.3", "1.2.3-dirty"))
        assertFalse(ReleaseInfo.isNewer("nightly", "1.0"))
        assertTrue(ReleaseInfo.isNewer("v1.0.0", "unknown"))
    }
}
