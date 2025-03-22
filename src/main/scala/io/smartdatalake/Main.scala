package io.smartdatalake

import ch.qos.logback.classic.Level
import io.smartdatalake.logging.{LoggerOutputStream, LoggingManager}
import io.smartdatalake.modules.AppModule
import jdk.jshell.spi.ExecutionControlProvider
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.{LanguageClient, LanguageClientAware, LanguageServer}

import java.io.{InputStream, OutputStream, PrintStream, PrintWriter}
import org.slf4j.LoggerFactory

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

/**
 * @author scalathe
 */

object Main extends AppModule {
  
  def main(args : Array[String]): Unit = {

    // We're using Standard Input and Standard Output for communication, so we need to ensure Standard Output is only used by the LSP4j server.
    // Keep a reference on the default standard output
    val systemOut = System.out

    // redirect default output to the same stream of the logback logger
    LoggingManager.redirectStandardOutputToLoggerOutput()

    // give the Standard Output reference for the server.
    startServer(System.in, systemOut)
  }

  private def startServer(in: InputStream, out: PrintStream) = {
    val logger = LoggerFactory.getLogger(getClass)
    val sdlbLanguageServer: LanguageServer & LanguageClientAware = languageServer

    try
      val launcher: Launcher[LanguageClient] = Launcher.Builder[LanguageClient]()
        .traceMessages(PrintWriter(LoggingManager.createPrintStreamWithLoggerName("jsonRpcLogger", level = Level.TRACE)))
        .setExecutorService(executorService)
        .setInput(in)
        .setOutput(out)
        .setRemoteInterface(classOf[LanguageClient])
        .setLocalService(sdlbLanguageServer)
        .create()

      val client: LanguageClient = launcher.getRemoteProxy
      sdlbLanguageServer.connect(client)
      // Use the configured logger
      logger.info("Server starts listening...")
      launcher.startListening().get()
    catch
      case NonFatal(ex) =>
        ex.printStackTrace(out)
        logger.error(ex.toString)

    finally
      // Might want to also give capabilities to let the server shutdown itself more properly
      executionContext.shutdownNow()
      executorService.shutdownNow()
      sys.exit(0)
  }

}
