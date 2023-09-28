package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigRenderOptions, ConfigUtil}
import io.smartdatalake.UnitSpec
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer as MLT

import scala.io.Source
import scala.util.Using

class SDLBContextSpec extends UnitSpec {

  private val basicText: String = loadFile("fixture/hocon/basic-example.conf")
  private val withListText: String = loadFile("fixture/hocon/with-lists-example.conf")


  "Smart DataLake Builder Context" should "creates a context with empty config if text is empty" in {
    SDLBContext.fromText("").textContext.rootConfig shouldBe HoconParser.EMPTY_CONFIG
  }

  it should "creates a context with empty config if text is invalid" in {
    SDLBContext.fromText("blah {").textContext.rootConfig shouldBe HoconParser.EMPTY_CONFIG
  }

  it should "not update the context if line is invalid" in {
    val initialContext = SDLBContext.fromText(basicText)
    initialContext.withCaretPosition(0, 1) shouldBe initialContext
    initialContext.withCaretPosition(23, 1) shouldBe initialContext
  }

  it should "not update the context if col is invalid" in {
    val initialContext = SDLBContext.fromText(basicText)
    initialContext.withCaretPosition(1, -1) shouldBe initialContext
  }

  it should "create a context correctly with a basic example" in {
    val line1Start = SDLBContext.fromText(basicText).withCaretPosition(1, 0)
    line1Start.parentPath shouldBe List()
    line1Start.word shouldBe "global"

    val line1End = SDLBContext.fromText(basicText).withCaretPosition(1, 999)
    line1End.parentPath shouldBe List("global")
    line1End.word shouldBe "{"

    val line3Start = SDLBContext.fromText(basicText).withCaretPosition(3, 0)
    line3Start.parentPath shouldBe List("global", "spark-options")
    line3Start.word shouldBe ""

    val line3End = SDLBContext.fromText(basicText).withCaretPosition(3, 999)
    line3End.parentPath shouldBe List("global", "spark-options", "spark.sql.shuffle.partitions")
    line3End.word shouldBe "2"

    val line5Start = SDLBContext.fromText(basicText).withCaretPosition(5, 0)
    line5Start.parentPath shouldBe List("global")
    line5Start.word shouldBe "}"

    val line5End = SDLBContext.fromText(basicText).withCaretPosition(5, 1)
    line5End.parentPath shouldBe List()
    line5End.word shouldBe "}"

  }

  it should "create a context correctly with lists" in {
    val line7End = SDLBContext.fromText(withListText).withCaretPosition(7, 999)
    line7End.parentPath shouldBe List("actions", "select-airport-cols", "transformers", "0", "type")
    line7End.word shouldBe "SQLDfTransformer"

    val line23EdgeInside = SDLBContext.fromText(withListText).withCaretPosition(23, 7)
    line23EdgeInside.parentPath shouldBe List("actions", "join-departures-airports", "transformers", "0")
    line23EdgeInside.word shouldBe "}},"

    val line24EdgeInsideAgain = SDLBContext.fromText(withListText).withCaretPosition(24, 7)
    line24EdgeInsideAgain.parentPath shouldBe List("actions", "join-departures-airports", "transformers", "1")
    line24EdgeInsideAgain.word shouldBe "{"
    
  }

  it should "create a context correctly when in a list but not in an element directly" in {
    val line23EdgeOutside = SDLBContext.fromText(withListText).withCaretPosition(23, 8)
    line23EdgeOutside.parentPath shouldBe List("actions", "join-departures-airports", "transformers")
    line23EdgeOutside.word shouldBe "}},"
    val line24StillEdgeOutside = SDLBContext.fromText(withListText).withCaretPosition(24, 6)
    line24StillEdgeOutside.parentPath shouldBe List("actions", "join-departures-airports", "transformers")
    line24StillEdgeOutside.word shouldBe "{" //line23EdgeOutside.textContext.rootConfig.root().render(ConfigRenderOptions.concise())
  }

}
