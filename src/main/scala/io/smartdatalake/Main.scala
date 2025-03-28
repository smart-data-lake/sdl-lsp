package io.smartdatalake

import ch.qos.logback.classic.Level
import io.smartdatalake.logging.{LoggerOutputStream, LoggingManager}
import io.smartdatalake.modules.AppModule
import io.smartdatalake.client.ClientType
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
    
  def main(args: Array[String]): Unit = {
    // Parse command line arguments
    val clientType = parseClientType(args)
    
    // We're using Standard Input and Standard Output for communication, so we need to ensure Standard Output is only used by the LSP4j server.
    // Keep a reference on the default standard output
    val systemOut = System.out

    // redirect default output to the same stream of the logback logger
    LoggingManager.redirectStandardOutputToLoggerOutput()

    // give the Standard Output reference for the server.
    startServer(System.in, systemOut, clientType)
  }
  
  private def parseClientType(args: Array[String]): ClientType = {
    val logger = LoggerFactory.getLogger(getClass)
    
    def parseArgs(remainingArgs: List[String]): ClientType = remainingArgs match {
      case "--client" :: clientName :: _ => 
        clientName.toLowerCase match {
          case "vscode" => 
            logger.info("Client identified as VSCode")
            ClientType.VSCode
          case "intellij" => 
            logger.info("Client identified as IntelliJ")
            ClientType.IntelliJ
          case unknown =>
            logger.warn(s"Unknown client type: $unknown, defaulting to Unknown")
            ClientType.Unknown
        }
      case _ :: tail => parseArgs(tail)
      case Nil => 
        logger.info("No client type specified, defaulting to Unknown")
        ClientType.Unknown
    }
    
    parseArgs(args.toList)
  }

  private def startServer(in: InputStream, out: PrintStream, clientType: ClientType) = {
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
      textDocumentService.clientType = clientType
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
