package io.smartdatalake

import io.smartdatalake.logging.LoggingManager
import io.smartdatalake.modules.AppModule
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.{LanguageClient, LanguageClientAware, LanguageServer}

import java.io.{InputStream, OutputStream, PrintStream}
import org.slf4j.LoggerFactory

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
    val helloLanguageServer: LanguageServer & LanguageClientAware = languageServer
    val launcher: Launcher[LanguageClient] = LSPLauncher.createServerLauncher(helloLanguageServer, in, out)
    val client: LanguageClient = launcher.getRemoteProxy

    helloLanguageServer.connect(client)
    // Use the configured logger
    val logger = LoggerFactory.getLogger(getClass)
    logger.info("Server starts listening...")
    launcher.startListening().get()
  }

}
