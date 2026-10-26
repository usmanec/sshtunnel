package top.rootu.ssh.tunnel.vpn.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** External IP address and its location as seen by a geo-IP service. */
data class GeoIpInfo(
    val ip: String,
    val countryCode: String?,
    val country: String?,
    val city: String?
)

/** A geo-IP web service: request URL for a language and a parser for its JSON answer. */
class GeoIpProvider(
    val name: String,
    val url: (language: String) -> String,
    val parse: (Map<String, Any?>) -> GeoIpInfo?
)

/**
 * Finds the external IP, country and city. All HTTP traffic goes through [connector], so the
 * caller decides the path: through the local SOCKS5 proxy (the tunnel's exit address) or direct.
 * Providers are tried in order until one answers.
 */
class GeoIpLookup(
    private val connector: (host: String, port: Int) -> Socket,
    private val language: String = Locale.getDefault().language,
    private val providers: List<GeoIpProvider> = DEFAULT_PROVIDERS,
    private val timeoutMs: Int = 10_000
) {
    companion object {
        private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

        val DEFAULT_PROVIDERS = listOf(
            GeoIpProvider(
                name = "ipwho.is",
                url = { lang -> "https://ipwho.is/?lang=$lang&fields=success,ip,country,country_code,city" },
                parse = { json ->
                    if (json["success"] == false) null
                    else json.str("ip")?.let { GeoIpInfo(it, json.str("country_code"), json.str("country"), json.str("city")) }
                }
            ),
            GeoIpProvider(
                name = "ipinfo.io",
                url = { "https://ipinfo.io/json" },
                // "country" is a two-letter code here
                parse = { json -> json.str("ip")?.let { GeoIpInfo(it, json.str("country"), null, json.str("city")) } }
            ),
            GeoIpProvider(
                name = "ip-api.com",
                // The free plan is HTTP only; used as the last resort
                url = { lang -> "http://ip-api.com/json/?lang=$lang&fields=status,query,country,countryCode,city" },
                parse = { json ->
                    if (json["status"] != "success") null
                    else json.str("query")?.let { GeoIpInfo(it, json.str("countryCode"), json.str("country"), json.str("city")) }
                }
            )
        )

        /** Languages the services can localize names to; everything else falls back to English. */
        private val SUPPORTED_LANGUAGES = setOf("en", "ru", "de", "es", "fr", "ja")
    }

    @Throws(IOException::class)
    fun lookup(): GeoIpInfo {
        val lang = language.lowercase(Locale.ROOT).takeIf { it in SUPPORTED_LANGUAGES } ?: "en"
        var lastError: Exception? = null
        for (provider in providers) {
            try {
                val body = MiniHttp.get(provider.url(lang), connector, timeoutMs)
                val info = provider.parse(MiniJson.parseObject(body)) ?: continue
                return info.copy(country = info.country ?: countryName(info.countryCode, lang))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("External IP lookup failed", lastError)
    }

    private fun countryName(code: String?, lang: String): String? {
        if (code.isNullOrBlank() || code.length != 2) return null
        return Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(Locale(lang)).takeIf { it.isNotBlank() }
    }
}

/** Minimal HTTP/1.0 GET over a caller-provided socket (HTTP or HTTPS with host name verification). */
object MiniHttp {
    private const val MAX_RESPONSE = 256 * 1024

    @Throws(IOException::class)
    fun get(url: String, connector: (host: String, port: Int) -> Socket, timeoutMs: Int): String {
        val uri = URI(url)
        val https = when (uri.scheme) {
            "https" -> true
            "http" -> false
            else -> throw IOException("Unsupported scheme in $url")
        }
        val host = uri.host ?: throw IOException("No host in $url")
        val port = if (uri.port > 0) uri.port else if (https) 443 else 80
        val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")

        var socket = connector(host, port)
        try {
            socket.soTimeout = timeoutMs
            if (https) {
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(socket, host, port, true) as SSLSocket
                socket = tls
                tls.startHandshake()
                // A layered TLS socket does not check the certificate name by itself
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.session)) {
                    throw SSLPeerUnverifiedException("Certificate does not match $host")
                }
            }
            val request = "GET $path HTTP/1.0\r\n" +
                "Host: $host\r\n" +
                "User-Agent: SSH-Tunnel\r\n" +
                "Accept: application/json\r\n" +
                "Connection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            return parseResponse(readAll(socket))
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readAll(socket: Socket): ByteArray {
        val input = socket.getInputStream()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_RESPONSE) throw IOException("Response too large")
        }
        return out.toByteArray()
    }

    /** Returns the body of a 2xx response. Handles chunked encoding just in case. */
    @Throws(IOException::class)
    fun parseResponse(raw: ByteArray): String {
        val headerEnd = indexOf(raw, "\r\n\r\n".toByteArray(), 0)
        if (headerEnd < 0) throw IOException("Malformed HTTP response")
        val headers = String(raw, 0, headerEnd, Charsets.ISO_8859_1).split("\r\n")
        val status = headers.first().split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Malformed HTTP status line")
        if (status !in 200..299) throw IOException("HTTP $status")
        var body = raw.copyOfRange(headerEnd + 4, raw.size)
        val chunked = headers.drop(1).any {
            it.substringBefore(':').trim().equals("Transfer-Encoding", true) &&
                it.substringAfter(':').contains("chunked", true)
        }
        if (chunked) body = decodeChunked(body)
        return String(body, Charsets.UTF_8)
    }

    private fun decodeChunked(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val lineEnd = indexOf(data, "\r\n".toByteArray(), pos)
            if (lineEnd < 0) break
            val size = String(data, pos, lineEnd - pos, Charsets.ISO_8859_1).substringBefore(';').trim().toInt(16)
            if (size == 0) break
            val start = lineEnd + 2
            if (start + size > data.size) throw IOException("Truncated chunk")
            out.write(data, start, size)
            pos = start + size + 2
        }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}

/** Small JSON reader (objects, arrays, strings, numbers, booleans, null) for the geo-IP answers. */
object MiniJson {
    @Throws(IOException::class)
    fun parseObject(text: String): Map<String, Any?> {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipWhitespace()
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: throw IOException("JSON object expected")
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun fail(): Nothing = throw IOException("Bad JSON at $i")

        fun value(): Any? {
            skipWhitespace()
            if (i >= s.length) fail()
            return when (s[i]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail()
            i += word.length
            return value
        }

        private fun number(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val text = s.substring(start, i)
            return text.toLongOrNull() ?: text.toDoubleOrNull() ?: fail()
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // {
            skipWhitespace()
            if (i < s.length && s[i] == '}') {
                i++
                return map
            }
            while (true) {
                skipWhitespace()
                if (i >= s.length || s[i] != '"') fail()
                val key = string()
                skipWhitespace()
                if (i >= s.length || s[i] != ':') fail()
                i++
                map[key] = value()
                skipWhitespace()
                if (i >= s.length) fail()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> fail()
                }
            }
        }

        private fun array(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // [
            skipWhitespace()
            if (i < s.length && s[i] == ']') {
                i++
                return list
            }
            while (true) {
                list += value()
                skipWhitespace()
                if (i >= s.length) fail()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> fail()
                }
            }
        }

        private fun string(): String {
            val sb = StringBuilder()
            i++ // opening quote
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail()
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) fail()
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail())
                                i += 4
                            }
                            else -> sb.append(e) // " \ /
                        }
                    }
                    else -> sb.append(c)
                }
            }
            fail()
        }
    }
}
