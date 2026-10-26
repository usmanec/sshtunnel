package com.jcraft.jsch;

/**
 * Per-channel SSH flow control for JSch.
 *
 * <p>JSch delivers incoming channel data on its single session thread and re-opens the SSH
 * window right after handing the data to the channel's output stream. If one consumer is slow
 * (for example a video player that paused loading), the session thread blocks on that stream
 * and every other channel stalls (head-of-line blocking).
 *
 * <p>This helper lives in the {@code com.jcraft.jsch} package to reach JSch's package-private
 * window fields. It lets a non-blocking output stream "close" the window of a single channel
 * while its buffer is full and re-open it once the consumer catches up.
 */
public final class ChannelWindowControl {

  private ChannelWindowControl() {}

  /** Sets the initial local window and packet size. Must be called before {@code connect()}. */
  public static void configure(Channel channel, int windowSize, int maxPacketSize) {
    channel.lwsize_max = windowSize;
    channel.lwsize = windowSize;
    channel.lmpsize = maxPacketSize;
  }

  /**
   * Stops JSch from automatically re-opening the window. Called from the channel's output
   * stream, i.e. on the session thread right before JSch checks whether to send
   * SSH_MSG_CHANNEL_WINDOW_ADJUST.
   */
  public static void pause(Channel channel) {
    channel.lwsize_max = 0;
  }

  /** Restores the window to {@code windowSize} and sends the missing credit to the server. */
  public static void resume(Channel channel, int windowSize) {
    synchronized (channel) {
      if (channel.close || !channel.isConnected()) {
        channel.lwsize_max = windowSize;
        return;
      }
      int credit = windowSize - channel.lwsize;
      channel.lwsize_max = windowSize;
      channel.setLocalWindowSize(windowSize);
      if (credit <= 0) {
        return;
      }
      int recipient = channel.getRecipient();
      if (recipient == -1) {
        return;
      }
      try {
        Buffer buf = new Buffer(100);
        Packet packet = new Packet(buf);
        packet.reset();
        buf.putByte((byte) Session.SSH_MSG_CHANNEL_WINDOW_ADJUST);
        buf.putInt(recipient);
        buf.putInt(credit);
        channel.getSession().write(packet);
      } catch (Exception ignored) {
        // The session is going down; the channel will be closed by JSch.
      }
    }
  }
}
