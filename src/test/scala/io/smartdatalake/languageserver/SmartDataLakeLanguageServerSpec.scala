package io.smartdatalake.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.client.ClientType
import io.smartdatalake.modules.TestModule
import org.eclipse.lsp4j.{ClientInfo, InitializeParams, InitializeResult}

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

  it should "detect the client type from the initialize request" in {
    def initializedClientType(clientName: String, commandLineClientType: ClientType = ClientType.Unknown): ClientType =
      val module = new TestModule {}
      module.textDocumentService.clientType = commandLineClientType
      val params = new InitializeParams()
      params.setClientInfo(new ClientInfo(clientName, "1.0"))
      module.languageServer.initialize(params).get()
      module.textDocumentService.clientType

    initializedClientType("IntelliJ IDEA") shouldBe ClientType.IntelliJ
    initializedClientType("Visual Studio Code") shouldBe ClientType.VSCode
    initializedClientType("Visual Studio Code", commandLineClientType = ClientType.IntelliJ) shouldBe ClientType.IntelliJ
  }

}
