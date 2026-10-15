package io.smartdatalake.logging


import java.io.{ByteArrayOutputStream, OutputStream}
import java.nio.charset.StandardCharsets

/** Writes each line to the given function, decoding bytes as UTF-8 */
private[logging] class LoggerOutputStream(write: String => Unit) extends OutputStream {
  private val buffer = new ByteArrayOutputStream()
  override def write(b: Int): Unit = synchronized {
    if (b == '\n') {
      write(buffer.toString(StandardCharsets.UTF_8).stripSuffix("\r"))
      buffer.reset()
    } else {
      buffer.write(b)
    }
  }

}
