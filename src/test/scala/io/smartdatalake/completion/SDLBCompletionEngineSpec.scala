package io.smartdatalake.completion

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import ujson.*

import scala.io.Source
import scala.util.Using

class SDLBCompletionEngineSpec extends UnitSpec {

  val completionEngine = new SDLBCompletionEngineImpl

  "SDLB Completion engine" should "retrieve all the properties of copyAction" in {
    val context = SDLBContext.fromText(loadFile("fixture/hocon/with-multi-lines-flattened-example.conf"))
      .withCaretPosition(16, 0)
    completionEngine.generateCompletionItems(context) should have size 12
  }

  it should "generate templates for actions" in {
    val deduplicateTemplate = completionEngine.generateTemplatesForAction().map(_.getInsertText).find(_.startsWith("deduplicate"))
    deduplicateTemplate should contain ("deduplicateaction_PLACEHOLDER {\n\t\ttype = DeduplicateAction\n\t\tinputId = \"???\"\n\t\toutputId = \"???\"\n\t}\n")
  }


}
