package io.smartdatalake.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.completion.ai.AICompletionEngineImpl
import io.smartdatalake.completion.ai.model.ModelClient
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.languageserver.workspace.WorkspaceManager

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import org.eclipse.lsp4j.{CompletionParams, DidOpenTextDocumentParams, HoverParams, Position,
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

  it should "precompute AI completions once and keep the completion data small" in {
    val calls = new AtomicInteger(0)
    val countingClient = new ModelClient:
      override def isEnabled: Boolean = true
      override def completeAsync(prompt: String, model: Option[String]): Future[String] =
        calls.incrementAndGet()
        Future.successful("""[{"tab_stop_number": 1, "new_value": "ai_name"}]""")
    val service = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, new AICompletionEngineImpl(countingClient), WorkspaceManager())
    val item = new TextDocumentItem("ai.conf", "hocon", 1, "dataObjects {\n  \n}")
    service.didOpen(new DidOpenTextDocumentParams(item))
    val completionParams = new CompletionParams(new TextDocumentIdentifier("ai.conf"), new Position(1, 2))

    val items = service.completion(completionParams).get().getLeft.toScala
    val tabStopItems = items.take(3).count(i => Option(i.getData).isDefined)
    tabStopItems should be > 0
    items.flatMap(i => Option(i.getData)).map(_.toString.length).max should be < 200
    calls.get() shouldBe tabStopItems

    service.completion(completionParams).get()
    calls.get() shouldBe tabStopItems

    val resolved = service.resolveCompletionItem(items.head).get()
    resolved.getInsertText should include ("${1:ai_name}")
    calls.get() shouldBe tabStopItems
  }

}
