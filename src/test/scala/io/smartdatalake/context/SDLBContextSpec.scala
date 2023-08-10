package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigRenderOptions, ConfigUtil}
import io.smartdatalake.UnitSpec
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer as MLT

import scala.io.Source
import scala.util.Using

class SDLBContextSpec extends UnitSpec {

  private val text: String = loadFile("fixture/hocon/basic-example.conf")


  "Smart DataLake Builder Context" should "creates a context with empty config if text is empty" in {
    SDLBContext.fromText("").textContext.config shouldBe HoconParser.EMPTY_CONFIG
  }

  it should "creates a context with empty config if text is invalid" in {
    SDLBContext.fromText("blah {").textContext.config shouldBe HoconParser.EMPTY_CONFIG
  }

  it should "not update the context if line is invalid" in {
    val initialContext = SDLBContext.fromText(text)
    initialContext.withCaretPosition(0, 1) shouldBe initialContext
    initialContext.withCaretPosition(23, 1) shouldBe initialContext
  }

  it should "not update the context if col is invalid" in {
    val initialContext = SDLBContext.fromText(text)
    initialContext.withCaretPosition(1, -1) shouldBe initialContext
  }

  it should "creates a context correctly with a basic example" in {
    val line1Start = SDLBContext.fromText(text).withCaretPosition(1, 0)
    line1Start.parentPath shouldBe ""
    line1Start.parentWord shouldBe ""
    line1Start.getParentContext shouldBe None

    val line1End = SDLBContext.fromText(text).withCaretPosition(1, 999)
    line1End.parentPath shouldBe "global"
    line1End.parentWord shouldBe "global"
    line1End.getParentContext shouldBe defined

    val line3Start = SDLBContext.fromText(text).withCaretPosition(3, 0)
    line3Start.parentPath shouldBe "global.spark-options"
    line3Start.parentWord shouldBe "spark-options"
    line3Start.getParentContext.get.unwrapped().asInstanceOf[java.util.HashMap[String, Int]].get("spark.sql.shuffle.partitions") shouldBe 2

    val line3End = SDLBContext.fromText(text).withCaretPosition(3, 999)
    line3End.parentPath shouldBe "global.spark-options.spark.sql.shuffle.partitions"
    line3End.parentWord shouldBe "\"spark.sql.shuffle.partitions\""
    //line3End.getParentContext shouldBe defined //TODO this one is a problem because of the key with dots

    val line5Start = SDLBContext.fromText(text).withCaretPosition(5, 0)
    line5Start.parentPath shouldBe "global"
    line5Start.parentWord shouldBe "global"
    line5Start.getParentContext shouldBe defined

    val line5End = SDLBContext.fromText(text).withCaretPosition(5, 1)
    line5End.parentPath shouldBe ""
    line5End.parentWord shouldBe ""
    line5End.getParentContext shouldBe None

  }

}
