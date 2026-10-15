package io.smartdatalake.logging

import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory

import java.io.PrintStream
import java.nio.charset.StandardCharsets

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
    PrintStream(LoggerOutputStream(printMethod), true, StandardCharsets.UTF_8)

  def isTraceEnabled(loggerName: String): Boolean = LoggerFactory.getLogger(loggerName).isTraceEnabled

  /** Changes the level of all loggers, e.g. "debug". Unknown levels are ignored */
  def setRootLevel(levelName: String): Unit =
    Option(Level.toLevel(levelName.trim, null)).foreach { level =>
      LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match
        case root: Logger => root.setLevel(level)
        case _ => ()
    }

}
