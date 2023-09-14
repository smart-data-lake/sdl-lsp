package io.smartdatalake.completion

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import ujson.*

import scala.io.Source
import scala.util.Using

class SDLBCompletionEngineSpec extends UnitSpec {
  
  "SDLB Completion engine" should "retrieve all the properties of copyAction" in {
    val context = SDLBContext.fromText(loadFile("fixture/hocon/with-multi-lines-flattened-example.conf"))
      .withCaretPosition(16, 0)
    completionEngine.generateCompletionItems(context) should have size 12
  }

  it should "do something" in { //TODO either rename or change. Or remove it.
    val context = SDLBContext.fromText(loadFile("fixture/hocon/with-lists-example.conf"))
    completionEngine.generateCompletionItems(context.withCaretPosition(3, 0)) should have size 9
    completionEngine.generateCompletionItems(context.withCaretPosition(7, 0)) should have size 4
  }


}
