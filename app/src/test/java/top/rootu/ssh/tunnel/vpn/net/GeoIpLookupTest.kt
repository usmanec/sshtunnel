package top.rootu.ssh.tunnel.vpn.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class GeoIpLookupTest {
    private val executor = Executors.newCachedThreadPool()
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val requests = CopyOnWriteArrayList<String>()
    private val connectedHosts = CopyOnWriteArrayList<String>()

    @After
    fun tearDown() {
        server.close()
        executor.shutdownNow()
    }

    /** Local HTTP server answering by path; the connector records which host was asked for. */
    private fun serve(responses: Map<String, String>) {
        executor.execute {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    return@execute
                }
                socket.use { s ->
                    val head = s.getInputStream().bufferedReader().readLine() ?: return@use
                    requests += head
                    val path = head.split(' ')[1]
                    val response = responses.entries.firstOrNull { path.startsWith(it.key) }?.value
                        ?: "HTTP/1.0 404 Not Found\r\n\r\n"
                    s.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    private val connector: (String, Int) -> Socket = { host, _ ->
        connectedHosts += host
        Socket(InetAddress.getLoopbackAddress(), server.localPort)
    }

    private fun provider(name: String, path: String, parse: (Map<String, Any?>) -> GeoIpInfo?) =
        GeoIpProvider(name, { lang -> "http://$name.test$path?lang=$lang" }, parse)

    @Test
    fun `falls back to the next provider and localizes the country name`() {
        serve(
            mapOf(
                "/broken" to "HTTP/1.0 500 Internal Server Error\r\n\r\n",
                "/ok" to "HTTP/1.0 200 OK\r\nContent-Type: application/json\r\n\r\n" +
                    """{"ip":"203.0.113.7","country":"DE","city":"Frankfurt am Main"}"""
            )
        )
        val providers = listOf(
            provider("first", "/broken") { null },
            provider("second", "/ok") { json -> GeoIpInfo(json["ip"] as String, json["country"] as String, null, json["city"] as String) }
        )
        val info = GeoIpLookup(connector, language = "ru", providers = providers).lookup()

        assertEquals("203.0.113.7", info.ip)
        assertEquals("Frankfurt am Main", info.city)
        assertEquals("DE", info.countryCode)
        assertEquals("Германия", info.country)
        assertEquals(listOf("first.test", "second.test"), connectedHosts)
        assertTrue(requests.all { it.contains("lang=ru") && it.endsWith("HTTP/1.0") })
    }

    @Test
    fun `unsupported language falls back to english`() {
        serve(mapOf("/ok" to "HTTP/1.0 200 OK\r\n\r\n{\"ip\":\"198.51.100.1\",\"cc\":\"FR\"}"))
        val providers = listOf(provider("p", "/ok") { json -> GeoIpInfo(json["ip"] as String, json["cc"] as String, null, null) })
        val info = GeoIpLookup(connector, language = "xx", providers = providers).lookup()
        assertEquals("France", info.country)
        assertTrue(requests.single().contains("lang=en"))
    }

    @Test
    fun `all providers failing is an error`() {
        serve(emptyMap())
        try {
            GeoIpLookup(connector, providers = listOf(provider("a", "/x") { null })).lookup()
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun `default providers parse their documented answers`() {
        val (ipwho, ipinfo, ipapi) = GeoIpLookup.DEFAULT_PROVIDERS
        assertEquals(
            GeoIpInfo("203.0.113.7", "RU", "Россия", "Москва"),
            ipwho.parse(MiniJson.parseObject("""{"success":true,"ip":"203.0.113.7","country":"Россия","country_code":"RU","city":"Москва"}"""))
        )
        assertNull(ipwho.parse(MiniJson.parseObject("""{"success":false,"message":"limit"}""")))
        assertEquals(
            GeoIpInfo("203.0.113.8", "NL", null, "Amsterdam"),
            ipinfo.parse(MiniJson.parseObject("""{"ip":"203.0.113.8","city":"Amsterdam","region":"North Holland","country":"NL","loc":"52.3,4.9"}"""))
        )
        assertEquals(
            GeoIpInfo("203.0.113.9", "FI", "Finland", "Helsinki"),
            ipapi.parse(MiniJson.parseObject("""{"status":"success","country":"Finland","countryCode":"FI","city":"Helsinki","query":"203.0.113.9"}"""))
        )
        assertNull(ipapi.parse(MiniJson.parseObject("""{"status":"fail","message":"private range"}""")))
        assertTrue(ipwho.url("ru").startsWith("https://"))
    }

    @Test
    fun `json parser handles escapes nesting and numbers`() {
        val json = MiniJson.parseObject(
            """ { "a": "x\"y\\z\u0041\n", "n": -12.5e1, "i": 42, "b": [true, false, null], "o": {"k": "v"} } """
        )
        assertEquals("x\"y\\zA\n", json["a"])
        assertEquals(-125.0, json["n"])
        assertEquals(42L, json["i"])
        assertEquals(listOf(true, false, null), json["b"])
        assertEquals(mapOf("k" to "v"), json["o"])
    }

    @Test
    fun `http parser decodes chunked bodies and rejects errors`() {
        val chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\n{\"a\"\r\n5\r\n:\"b\"}\r\n0\r\n\r\n"
        assertEquals("{\"a\":\"b\"}", MiniHttp.parseResponse(chunked.toByteArray()))
        try {
            MiniHttp.parseResponse("HTTP/1.1 429 Too Many Requests\r\n\r\n".toByteArray())
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("HTTP 429", e.message)
        }
    }
}
