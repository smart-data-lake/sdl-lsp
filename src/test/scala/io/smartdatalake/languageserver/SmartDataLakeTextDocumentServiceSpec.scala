package io.smartdatalake.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.languageserver.workspace.WorkspaceManager

import java.lang.reflect.Proxy
import java.util
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.{CompletionParams, DidChangeTextDocumentParams, DidCloseTextDocumentParams, DidOpenTextDocumentParams, PublishDiagnosticsParams, TextDocumentContentChangeEvent, VersionedTextDocumentIdentifier, HoverParams, Position,
  TextDocumentIdentifier, TextDocumentItem}

class SmartDataLakeTextDocumentServiceSpec extends UnitSpec {
  
  def params: CompletionParams =
    val p = new CompletionParams()
    // Careful, Position of LSP4J is 0-based
    p.setPosition(new Position(16, 0))
    val textDocumentIdentifier = new TextDocumentIdentifier()
    textDocumentIdentifier.setUri("example.conf")
    p.setTextDocument(textDocumentIdentifier)
    p

  "SDL text document service" should "suggest at least one autocompletion item" in {
    notifyOpenFile()
    val completionResult = textDocumentService.completion(params)
    assert(completionResult.get.isLeft)
    assert(completionResult.get().getLeft.size() > 0)
  }

  it should "provide hovering information" in {
    notifyOpenFile()
    val params = new HoverParams()
    // Careful, Position of LSP4J is 0-based
    params.setPosition(new Position(5, 4))
    val textDocumentIdentifier = new TextDocumentIdentifier()
    textDocumentIdentifier.setUri("example.conf")
    params.setTextDocument(textDocumentIdentifier)
    val hoverInformation = textDocumentService.hover(params)
    assert(!hoverInformation.get().getContents.getRight.getValue.isBlank)
  }

  private def notifyOpenFile(): Unit = {
    val didOpenTextDocumentParams: DidOpenTextDocumentParams = new DidOpenTextDocumentParams()
    val textDocumentItem: TextDocumentItem = new TextDocumentItem()
    textDocumentItem.setText(loadFile("fixture/hocon/with-multi-lines-example.conf"))
    textDocumentItem.setUri("example.conf")
    didOpenTextDocumentParams.setTextDocument(textDocumentItem)
    textDocumentService.didOpen(didOpenTextDocumentParams)
  }

  it should "publish diagnostics of opened documents" in {
    val published = new LinkedBlockingQueue[PublishDiagnosticsParams]()
    val client = Proxy.newProxyInstance(getClass.getClassLoader, Array(classOf[LanguageClient]), (_, method, args) =>
      if method.getName == "publishDiagnostics" then published.put(args(0).asInstanceOf[PublishDiagnosticsParams])
      null
    ).asInstanceOf[LanguageClient]
    val service = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, WorkspaceManager(), navigationEngine, diagnosticsEngine)
    service.connect(client)
    def nextMessages(): List[String] = Option(published.poll(5, TimeUnit.SECONDS)).value.getDiagnostics.toScala.map(_.getMessage.getLeft)

    val text = "actions {\n  a1 { type = CopyAction, inputId = unknown-obj, outputId = unknown-obj }\n}"
    service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem("diag.conf", "hocon", 1, text)))
    nextMessages() shouldBe List("Unknown dataObject 'unknown-obj'", "Unknown dataObject 'unknown-obj'")

    val fixed = text + "\ndataObjects {\n  unknown-obj { type = CsvFileDataObject }\n}"
    service.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier("diag.conf", 2), util.List.of(new TextDocumentContentChangeEvent(fixed))))
    nextMessages() shouldBe empty

    service.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier("diag.conf")))
    nextMessages() shouldBe empty
  }

}
