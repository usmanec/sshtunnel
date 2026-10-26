package top.rootu.ssh.tunnel.vpn

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Something that can open TCP streams to remote hosts: the SSH session in production. */
interface TunnelTransport {
    val isConnected: Boolean

    /** Opens a stream to [host]:[port] (resolved on the remote side). Blocks until it is ready. */
    @Throws(IOException::class)
    fun openStream(host: String, port: Int): TunnelStream
}

/** A bidirectional byte stream through the tunnel. Closing [output] half-closes it. */
interface TunnelStream : Closeable {
    val input: InputStream
    val output: OutputStream
    /** True once the remote side has fully closed the stream. */
    val isClosed: Boolean
}
