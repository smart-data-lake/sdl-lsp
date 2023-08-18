package io.smartdatalake.hover

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import ujson.*

import scala.io.Source
import scala.util.Using

class SDLBHoverEngineSpec extends UnitSpec {

  val hoveringEngine = new SDLBHoverEngineImpl

  "SDLB Completion engine" should "retrieve all the properties of copyAction" in {
    val context = SDLBContext.fromText(loadFile("fixture/hocon/with-multi-lines-flattened-example.conf"))
      .withCaretPosition(6, 4)
    val expected =
      """Configuration of a custom Spark-DataFrame transformation between many inputs and many outputs (n:m).
        |Define a transform function which receives a map of input DataObjectIds with DataFrames and a map of options and has
        |to return a map of output DataObjectIds with DataFrames, see also trait[[CustomDfsTransformer]] .""".stripMargin
    //hoveringEngine.generateHoveringInformation(context).getContents.getRight.getValue shouldBe expected
  }


}
