package io.smartdatalake.context.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.languageserver.SmartDataLakeLanguageServer
import org.eclipse.lsp4j.InitializeResult

import java.util.concurrent.CompletableFuture

class SmartDataLakeLanguageServerSpec extends UnitSpec {

  def sdlLanguageServer = new SmartDataLakeLanguageServer

  "SDL Language Server" should "have autocompletion as a capability" in {
    val capabilities: CompletableFuture[InitializeResult] = sdlLanguageServer.initialize(null)
    capabilities.get().getCapabilities.getCompletionProvider shouldNot be (null)
  }

  it should "exit without errors if shutdown before" in {
    val server = sdlLanguageServer
    server.shutdown()
    server.getErrorCode should be (0)
  }

}
