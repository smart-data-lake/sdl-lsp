package io.smartdatalake.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.languageserver.SmartDataLakeLanguageServer
import org.eclipse.lsp4j.InitializeResult

import java.util.concurrent.CompletableFuture

class SmartDataLakeLanguageServerSpec extends UnitSpec {
  
  "SDL Language Server" should "have autocompletion as a capability" in {
    val capabilities: CompletableFuture[InitializeResult] = languageServer.initialize(null)
    capabilities.get().getCapabilities.getCompletionProvider shouldNot be (null)
  }

  "SDL Language Server" should "have hovering as a capability" in {
    val capabilities: CompletableFuture[InitializeResult] = languageServer.initialize(null)
    capabilities.get().getCapabilities.getHoverProvider.getLeft shouldBe true
  }

  it should "exit without errors if shutdown before" in {
    val server = languageServer
    server.shutdown()
    server.getErrorCode should be (0)
  }

}
