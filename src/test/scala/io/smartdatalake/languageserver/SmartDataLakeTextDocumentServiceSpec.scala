package io.smartdatalake.languageserver

import io.smartdatalake.UnitSpec
import io.smartdatalake.languageserver.SmartDataLakeTextDocumentService
import org.eclipse.lsp4j.{CompletionParams, DidOpenTextDocumentParams, Position, TextDocumentItem}

class SmartDataLakeTextDocumentServiceSpec extends UnitSpec {

  val textDocumentService = new SmartDataLakeTextDocumentService

  def params: CompletionParams =
    val p = new CompletionParams()
    p.setPosition(new Position(16, 0))
    p

  "SDL text document service" should "suggest at least one autocompletion item" in {
    val didOpenTextDocumentParams: DidOpenTextDocumentParams = new DidOpenTextDocumentParams()
    val textDocumentItem: TextDocumentItem = new TextDocumentItem()
    textDocumentItem.setText(loadFile("fixture/hocon/with-multi-lines-example.conf"))
    didOpenTextDocumentParams.setTextDocument(textDocumentItem)
    textDocumentService.didOpen(didOpenTextDocumentParams)
    val completionResult = textDocumentService.completion(params)
    assert(completionResult.get.isLeft)
    assert(completionResult.get().getLeft.size() > 0)
  }

}
