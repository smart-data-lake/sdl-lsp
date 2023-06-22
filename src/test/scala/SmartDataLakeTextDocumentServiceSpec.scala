import io.smartdatalake.SmartDataLakeTextDocumentService
import org.eclipse.lsp4j.{CompletionParams, Position}

class SmartDataLakeTextDocumentServiceSpec extends UnitSpec {

  val textDocumentService = new SmartDataLakeTextDocumentService

  def params: CompletionParams =
    val p = new CompletionParams()
    p.setPosition(new Position(0, 0))
    p

  "SDL text document service" should "suggest at least one autocompletion item" in {
    val completionResult = textDocumentService.completion(params)
    assert(completionResult.get.isLeft)
    assert(completionResult.get().getLeft.size() > 0)
  }

}
