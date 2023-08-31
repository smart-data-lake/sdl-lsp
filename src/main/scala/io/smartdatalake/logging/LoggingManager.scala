package io.smartdatalake.logging

import ch.qos.logback.classic.Level
import org.slf4j.LoggerFactory

import java.io.PrintStream

object LoggingManager {

  def redirectStandardOutputToLoggerOutput(): Unit =
    val redirectedPrintStream = createPrintStreamWithLoggerName("redirectedOutput")
    System.setOut(redirectedPrintStream)
    println("Using new default output stream")

  def createPrintStreamWithLoggerName(loggerName: String, level: Level = Level.INFO): PrintStream =
    val logger = LoggerFactory.getLogger(loggerName)
    val printMethod: String => Unit = level match
      case Level.TRACE => logger.trace
      case Level.DEBUG => logger.debug
      case Level.INFO => logger.info
      case Level.WARN => logger.warn
      case Level.ERROR => logger.error
    PrintStream(LoggerOutputStream(printMethod))

}
