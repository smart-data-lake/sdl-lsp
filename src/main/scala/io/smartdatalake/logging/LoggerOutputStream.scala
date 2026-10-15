package io.smartdatalake.logging


import java.io.OutputStream

private[logging] class LoggerOutputStream(write: String => Unit) extends OutputStream {
  private val builder = new StringBuilder
  override def write(b: Int): Unit = {
    if (b == '\n') {
      write(builder.toString)
      builder.clear()
    } else {
      builder.append(b.toChar)
    }
  }
  
}
