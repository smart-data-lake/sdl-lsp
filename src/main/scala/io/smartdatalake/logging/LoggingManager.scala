package io.smartdatalake.logging

import org.slf4j.LoggerFactory

import java.io.PrintStream

object LoggingManager {

  def redirectStandardOutputToLoggerOutput(): Unit =
    val loggerRedirectedOutput = LoggerFactory.getLogger("redirectedOutput")
    val redirectedPrintStream = new PrintStream(new LoggerOutputStream(loggerRedirectedOutput))
    System.setOut(redirectedPrintStream)
    println("Using new default output stream")

}
