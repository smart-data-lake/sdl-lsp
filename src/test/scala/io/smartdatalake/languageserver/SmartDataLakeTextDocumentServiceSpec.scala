package io.smartdatalake.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.languageserver.SmartDataLakeTextDocumentService
import org.eclipse.lsp4j.{CompletionParams, DidOpenTextDocumentParams, HoverParams, Position, TextDocumentItem}

class SmartDataLakeTextDocumentServiceSpec extends UnitSpec {

  val textDocumentService = new SmartDataLakeTextDocumentService

  def params: CompletionParams =
    val p = new CompletionParams()
    // Careful, Position of LSP4J is 0-based
    p.setPosition(new Position(16, 0))
    p

  "SDL text document service" should "suggest at least one autocompletion item" in {
    notifyOpenFile()
    val completionResult = textDocumentService.completion(params)
    assert(completionResult.get.isLeft)
    assert(completionResult.get().getLeft.size() > 0)
  }

  it should "provides hovering information" in {
    notifyOpenFile()
    val params = new HoverParams()
    // Careful, Position of LSP4J is 0-based
    params.setPosition(new Position(5, 4))
    val hoverInformation = textDocumentService.hover(params)
    assert(!hoverInformation.get().getContents.getRight.getValue.isBlank)
  }
  private def notifyOpenFile(): Unit = {
    val didOpenTextDocumentParams: DidOpenTextDocumentParams = new DidOpenTextDocumentParams()
    val textDocumentItem: TextDocumentItem = new TextDocumentItem()
    textDocumentItem.setText(loadFile("fixture/hocon/with-multi-lines-example.conf"))
    didOpenTextDocumentParams.setTextDocument(textDocumentItem)
    textDocumentService.didOpen(didOpenTextDocumentParams)
  }


}
