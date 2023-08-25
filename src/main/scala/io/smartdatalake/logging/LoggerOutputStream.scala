package io.smartdatalake.logging

import org.slf4j.Logger

import java.io.OutputStream

private[logging] class LoggerOutputStream(logger: Logger) extends OutputStream {
  private val builder = new StringBuilder
  override def write(b: Int): Unit = {
    if (b == '\n') {
      logger.info(builder.toString)
      builder.clear()
    } else {
      builder.append(b.toChar)
    }
  }
  
}
