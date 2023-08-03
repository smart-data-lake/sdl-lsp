package io.smartdatalake.completion

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import ujson.*

import scala.io.Source
import scala.util.Using

class SDLBCompletionEngineSpec extends UnitSpec {

  val completionEngine = new SDLBCompletionEngineImpl

  "SDLB Completion engine" should "retrieve all the properties of copyAction" in {
    val context = SDLBContext.createContext(loadFile("fixture/hocon/with-multi-lines-flattened-example.conf"), 16, 0)
    println(context.parentPath + " " + context.parentWord)
    println(completionEngine.generateCompletionItems(context))
    //TODO
  }


}
