package io.smartdatalake

import io.smartdatalake.modules.AppModule
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.{LanguageClient, LanguageClientAware, LanguageServer}

import java.io.{InputStream, PrintStream}
import java.util.logging.{Level, LogManager, Logger}

/**
 * @author scalathe
 */
object Main extends AppModule {
  
  def main(args : Array[String]): Unit = {

    // We're using Standard Input and Standard Output for communication.
    LogManager.getLogManager.reset()
    val globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)
    globalLogger.setLevel(Level.OFF)

    startServer(System.in, System.out)
  }

  private def startServer(in: InputStream, out: PrintStream) = {
    val helloLanguageServer: LanguageServer & LanguageClientAware = languageServer
    val launcher: Launcher[LanguageClient] = LSPLauncher.createServerLauncher(helloLanguageServer, in, out)
    val client: LanguageClient = launcher.getRemoteProxy

    helloLanguageServer.connect(client)

    launcher.startListening().get()
  }

}
