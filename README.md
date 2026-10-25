# SSH Tunnel

Android / Android TV app: a VPN over an SSH tunnel ("ssh -D") with per-app routing.

## How traffic flows

```
app (routed) ──► TUN 10.240.0.1 ──► VpnTunPacketHandler ──► SOCKS5 127.0.0.1:<port> ──► SSH direct-tcpip ──► server
```

- **TCP (IPv4)** is terminated by a userspace TCP endpoint (`vpn/net/TcpProxySession`) and relayed
  through the local SOCKS5 proxy into an SSH `direct-tcpip` channel.
- **DNS** (UDP port 53, to any resolver) is sent as DNS-over-TCP through the same SOCKS5 → SSH path,
  with a small cache.
- **Other UDP** (QUIC, NTP, ...) gets an ICMP "port unreachable" so apps fall back to TCP immediately.
- **IPv6** is not configured on the TUN, so Android blocks it for routed apps (no IPv6 leaks).
- While SSH reconnects the TUN stays up: routed apps get TCP resets instead of silently using the
  direct connection (kill switch).
- With "Bypass Local Network" on, private ranges (10/8, 172.16/12, 192.168/16, link-local,
  multicast) go directly to the LAN. Everything else goes through the tunnel.

Each SSH channel has its own flow control (`SshChannelBuffer` + `com.jcraft.jsch.ChannelWindowControl`),
so a paused stream (e.g. a video player with a full buffer) does not stall other connections.

## Android TV

- Everything works with the remote: arrows to move, OK to select/toggle, Back returns to Home.
- If the TV firmware has no VPN permission dialog, grant it once over ADB:
  `adb shell appops set top.rootu.ssh.tunnel ACTIVATE_VPN allow`
- Enable "Auto-Start on Boot" or Always-on VPN in Android settings to connect after power on.

## Tests

`app/src/test/java/com/sshtunnel/vpn` contains plain JVM tests for packet parsing, the TCP proxy
(windowing, retransmission, half-close), DNS caching and an end-to-end TUN → SOCKS5 → tunnel run.
