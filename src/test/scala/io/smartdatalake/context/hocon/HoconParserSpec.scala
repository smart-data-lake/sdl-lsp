package io.smartdatalake.context.hocon

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigRenderOptions, ConfigUtil}
import io.smartdatalake.UnitSpec
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

import scala.io.Source
import scala.util.Using

class HoconParserSpec extends UnitSpec {

  val (leftCol, rightCol) = (0, 999)
  case class CaretData(line: Int, column: Int, parentLine: Int, parentName: String, pathList: List[String], oIndex: Option[Int]=None)
  case class Fixture(originalText: String, text: String, config: Config)

  "Hocon parser" should "find path in hocon file" in {
    val fixture = loadFixture("fixture/hocon/basic-example.conf")

    val leftCaretData = List(
      CaretData(1, leftCol, 0, "", List()),
      CaretData(2, leftCol, 1, "global", List("global")),
      CaretData(3, leftCol, 2, "spark-options", List("global", "spark-options")),
      CaretData(4, leftCol, 2, "spark-options", List("global", "spark-options")),
      CaretData(5, leftCol, 1, "global", List("global"))
    )

    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, 1, "global", List("global")),
      CaretData(2, rightCol, 2, "spark-options", List("global", "spark-options")),
      CaretData(3, rightCol, 3, "\"spark.sql.shuffle.partitions\"", List("global", "spark-options", "spark.sql.shuffle.partitions")),
      CaretData(4, rightCol, 1, "global", List("global")),
      CaretData(5, rightCol, 0, "", List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "find path in file with comments" in {
    val fixture = loadFixture("fixture/hocon/with-comments-example.conf")

    val leftCaretData = List(
      CaretData(1, leftCol, 0, "", List()),
      CaretData(2, leftCol, 0, "", List()),
      CaretData(3, leftCol, 0, "", List()),
      CaretData(4, leftCol, 0, "", List()),
      CaretData(5, leftCol, 4, "global", List("global")),
      CaretData(6, leftCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(7, leftCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(8, leftCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(9, leftCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(10, leftCol, 4, "global", List("global"))
    )
    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, 0, "", List()),
      CaretData(2, rightCol, 0, "", List()),
      CaretData(3, rightCol, 0, "", List()),
      CaretData(4, rightCol, 4, "global", List("global")),
      CaretData(5, rightCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(6, rightCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(7, rightCol, 7, "\"spark.sql.shuffle.partitions\"", List("global", "spark-options", "spark.sql.shuffle.partitions")),
      CaretData(8, rightCol, 5, "spark-options", List("global", "spark-options")),
      CaretData(9, rightCol, 4, "global", List("global")),
      CaretData(10, rightCol, 0, "", List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "find path in with multi-line values" in {
    val fixture = loadFixture("fixture/hocon/with-multi-lines-example.conf")
    val positionMap = MultiLineTransformer.computeCorrectedPositions(fixture.originalText)

    val leftCaretData = List(
      CaretData(positionMap( 0)(0), positionMap( 0)(1) + leftCol, 0, "", List()),
      CaretData(positionMap( 1)(0), positionMap( 1)(1) + leftCol, 1, "actions", List("actions")),
      CaretData(positionMap( 2)(0), positionMap( 2)(1) + leftCol, 1, "actions", List("actions")),
      CaretData(positionMap( 3)(0), positionMap( 3)(1) + leftCol, 3, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(positionMap( 4)(0), positionMap( 4)(1) + leftCol, 3, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(positionMap( 5)(0), positionMap( 5)(1) + leftCol, 3, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(positionMap( 6)(0), positionMap( 6)(1) + leftCol, 6, "transformer", List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap( 7)(0), positionMap( 7)(1) + leftCol, 6, "transformer", List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap( 8)(0), positionMap( 8)(1) + leftCol, 8, "code", List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(positionMap( 9)(0), positionMap( 9)(1) + leftCol, 9,  "btl-connected-airports", List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(10)(0), positionMap(10)(1) + leftCol, 9,  "btl-connected-airports", List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(11)(0), positionMap(11)(1) + leftCol, 8,  "code", List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(positionMap(12)(0), positionMap(12)(1) + leftCol, 6,  "transformer", List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap(13)(0), positionMap(13)(1) + leftCol, 3,  "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(positionMap(14)(0), positionMap(14)(1) + leftCol, 1,  "actions", List("actions")),
      CaretData(positionMap(15)(0), positionMap(15)(1) + leftCol, 1,  "actions", List("actions")),
      CaretData(positionMap(16)(0), positionMap(16)(1) + leftCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(17)(0), positionMap(17)(1) + leftCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(18)(0), positionMap(18)(1) + leftCol, 16, "code", List("actions", "compute-distances", "code")),
      CaretData(positionMap(19)(0), positionMap(19)(1) + leftCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(20)(0), positionMap(20)(1) + leftCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(21)(0), positionMap(21)(1) + leftCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(22)(0), positionMap(22)(1) + leftCol, 16, "code", List("actions", "compute-distances", "code")),
      CaretData(positionMap(23)(0), positionMap(23)(1) + leftCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(24)(0), positionMap(24)(1) + leftCol, 19, "metadata", List("actions", "compute-distances", "metadata")),
      CaretData(positionMap(25)(0), positionMap(25)(1) + leftCol, 19, "metadata", List("actions", "compute-distances", "metadata")),
      CaretData(positionMap(26)(0), positionMap(26)(1) + leftCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(27)(0), positionMap(27)(1) + leftCol, 1,  "actions", List("actions"))
    )

    validateText(fixture, leftCol, leftCaretData, positionMap=Some(positionMap))

    val rightCaretData = List(
      CaretData(positionMap( 0)(0), positionMap( 0)(1) + rightCol, 1, "actions", List("actions")),
      CaretData(positionMap( 1)(0), positionMap( 1)(1) + rightCol, 1, "actions", List("actions")),
      CaretData(positionMap( 2)(0), positionMap( 2)(1) + rightCol, 3, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(positionMap( 3)(0), positionMap( 3)(1) + rightCol, 4, "type", List("actions", "join-departures-airports", "type")),
      CaretData(positionMap( 4)(0), positionMap( 4)(1) + rightCol, 5, "inputIds", List("actions", "join-departures-airports", "inputIds")),
      CaretData(positionMap( 5)(0), positionMap( 5)(1) + rightCol, 6, "transformer", List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap( 6)(0), positionMap( 6)(1) + rightCol, 7, "className", List("actions", "join-departures-airports", "transformer", "className")),
      CaretData(positionMap( 7)(0), positionMap( 7)(1) + rightCol, 8, "code", List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(positionMap( 8)(0), positionMap( 8)(1) + rightCol, 9, "btl-connected-airports", List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap( 9)(0), positionMap( 9)(1) + rightCol, 9, "btl-connected-airports", List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(10)(0), positionMap(10)(1) + rightCol, 9, "btl-connected-airports", List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(11)(0), positionMap(11)(1) + rightCol, 6, "transformer", List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap(12)(0), positionMap(12)(1) + rightCol, 3, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(positionMap(13)(0), positionMap(13)(1) + rightCol, 1, "actions", List("actions")),
      CaretData(positionMap(14)(0), positionMap(14)(1) + rightCol, 1, "actions", List("actions")),
      CaretData(positionMap(15)(0), positionMap(15)(1) + rightCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(16)(0), positionMap(16)(1) + rightCol, 15, "type", List("actions", "compute-distances", "type")),
      CaretData(positionMap(17)(0), positionMap(17)(1) + rightCol, 16, "code", List("actions", "compute-distances", "code")),
      CaretData(positionMap(18)(0), positionMap(18)(1) + rightCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(19)(0), positionMap(19)(1) + rightCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(20)(0), positionMap(20)(1) + rightCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(21)(0), positionMap(21)(1) + rightCol, 17, "btl-departures-arrivals-airports", List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(22)(0), positionMap(22)(1) + rightCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(23)(0), positionMap(23)(1) + rightCol, 19, "metadata", List("actions", "compute-distances", "metadata")),
      CaretData(positionMap(24)(0), positionMap(24)(1) + rightCol, 20, "feed", List("actions", "compute-distances", "metadata", "feed")),
      CaretData(positionMap(25)(0), positionMap(25)(1) + rightCol, 14, "compute-distances", List("actions", "compute-distances")),
      CaretData(positionMap(26)(0), positionMap(26)(1) + rightCol, 1, "actions", List("actions")),
      CaretData(positionMap(27)(0), positionMap(27)(1) + rightCol, 0, "", List())
    )
    validateText(fixture, rightCol, rightCaretData, positionMap=Some(positionMap))

  }


  it should "find path in file with lists" in {
    val fixture = loadFixture("fixture/hocon/with-lists-example.conf")
    
    val leftCaretData = List(
      CaretData(1, leftCol, 0, "", List()),
      CaretData(2, leftCol, 1, "actions", List("actions")),
      CaretData(3, leftCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(4, leftCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(5, leftCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(6, leftCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(7, leftCol, 6, "transformers", List("actions", "select-airport-cols", "transformers"), Some(0)),
      CaretData(8, leftCol, 6, "transformers", List("actions", "select-airport-cols", "transformers"), Some(0)),
      CaretData(9, leftCol, 6, "transformers", List("actions", "select-airport-cols", "transformers"), Some(0)),
      CaretData(10, leftCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(11, leftCol, 10, "metadata", List("actions", "select-airport-cols", "metadata")),
      CaretData(12, leftCol, 10, "metadata", List("actions", "select-airport-cols", "metadata")),
      CaretData(13, leftCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(14, leftCol, 1, "actions", List("actions")),
      CaretData(15, leftCol, 1, "actions", List("actions")),
      CaretData(16, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(17, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(18, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(19, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(20, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(0)),
      CaretData(21, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(0)),
      CaretData(22, leftCol, 21, "code", List("actions", "join-departures-airports", "transformers", "0", "code"), Some(0)),
      CaretData(23, leftCol, 21, "code", List("actions", "join-departures-airports", "transformers", "0", "code"), Some(0)),
      CaretData(24, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers")),
      CaretData(25, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(1)),
      CaretData(26, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(1)),
      CaretData(27, leftCol, 26, "code", List("actions", "join-departures-airports", "transformers", "1", "code"), Some(1)),
      CaretData(28, leftCol, 26, "code", List("actions", "join-departures-airports", "transformers", "1", "code"), Some(1)),
      CaretData(29, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(1)),
      CaretData(30, leftCol, 19, "transformers", List("actions", "join-departures-airports", "transformers")),
      CaretData(31, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(32, leftCol, 31, "metadata", List("actions", "join-departures-airports", "metadata")),
      CaretData(33, leftCol, 31, "metadata", List("actions", "join-departures-airports", "metadata")),
      CaretData(34, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(35, leftCol, 34, "executionMode", List("actions", "join-departures-airports", "executionMode")),
      CaretData(36, leftCol, 34, "executionMode", List("actions", "join-departures-airports", "executionMode")),
      CaretData(37, leftCol, 34, "executionMode", List("actions", "join-departures-airports", "executionMode")),
      CaretData(38, leftCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(39, leftCol,  1, "actions", List("actions"))
    )

    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, 1, "actions", List("actions")),
      CaretData(2, rightCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(3, rightCol, 3, "type", List("actions", "select-airport-cols", "type")),
      CaretData(4, rightCol, 4, "inputId", List("actions", "select-airport-cols", "inputId")),
      CaretData(5, rightCol, 5, "outputId", List("actions", "select-airport-cols", "outputId")),
      CaretData(6, rightCol, 6, "transformers", List("actions", "select-airport-cols", "transformers"), Some(0)),
      CaretData(7, rightCol, 7, "type", List("actions", "select-airport-cols", "transformers", "0", "type"), Some(0)),
      CaretData(8, rightCol, 8, "code", List("actions", "select-airport-cols", "transformers", "0", "code"), Some(0)),
      CaretData(9, rightCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(10, rightCol, 10, "metadata", List("actions", "select-airport-cols", "metadata")),
      CaretData(11, rightCol, 11, "feed", List("actions", "select-airport-cols", "metadata", "feed")),
      CaretData(12, rightCol, 2, "select-airport-cols", List("actions", "select-airport-cols")),
      CaretData(13, rightCol, 1, "actions", List("actions")),
      CaretData(14, rightCol, 1, "actions", List("actions")),
      CaretData(15, rightCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(16, rightCol, 16, "type", List("actions", "join-departures-airports", "type")),
      CaretData(17, rightCol, 17, "inputIds", List("actions", "join-departures-airports", "inputIds")),
      CaretData(18, rightCol, 18, "outputIds", List("actions", "join-departures-airports", "outputIds")),
      CaretData(19, rightCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(0)),
      CaretData(20, rightCol, 20, "type", List("actions", "join-departures-airports", "transformers", "0", "type"), Some(0)),
      CaretData(21, rightCol, 21, "code", List("actions", "join-departures-airports", "transformers", "0", "code"), Some(0)),
      CaretData(22, rightCol, 22, "btl-connected-airports", List("actions", "join-departures-airports", "transformers", "0", "code", "btl-connected-airports"), Some(0)),
      CaretData(23, rightCol, 19, "transformers", List("actions", "join-departures-airports", "transformers")),
      CaretData(24, rightCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(1)),
      CaretData(25, rightCol, 25, "type", List("actions", "join-departures-airports", "transformers", "1", "type"), Some(1)),
      CaretData(26, rightCol, 26, "code", List("actions", "join-departures-airports", "transformers", "1", "code"), Some(1)),
      CaretData(27, rightCol, 27, "btl-departures-arrivals-airports", List("actions", "join-departures-airports", "transformers", "1", "code", "btl-departures-arrivals-airports"), Some(1)),
      CaretData(28, rightCol, 19, "transformers", List("actions", "join-departures-airports", "transformers"), Some(1)),
      CaretData(29, rightCol, 19, "transformers", List("actions", "join-departures-airports", "transformers")),
      CaretData(30, rightCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(31, rightCol, 31, "metadata", List("actions", "join-departures-airports", "metadata")),
      CaretData(32, rightCol, 32, "feed", List("actions", "join-departures-airports", "metadata", "feed")),
      CaretData(33, rightCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(34, rightCol, 34, "executionMode", List("actions", "join-departures-airports", "executionMode")),
      CaretData(35, rightCol, 35, "type", List("actions", "join-departures-airports", "executionMode", "type")),
      CaretData(36, rightCol, 36, "className", List("actions", "join-departures-airports", "executionMode", "className")),
      CaretData(37, rightCol, 15, "join-departures-airports", List("actions", "join-departures-airports")),
      CaretData(38, rightCol, 1, "actions", List("actions")),
      CaretData(39, rightCol, 0, "", List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "parse silently empty files" in {
    HoconParser.parse("") shouldBe defined
  }

  it should "parse silently incorrect files" in {
    HoconParser.parse("blah {") shouldBe None
  }

  it should "retrieve correctly hovered word in simple case" in {
    val simpleText = "Hello SDLB!"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 0) shouldBe "Hello"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 4) shouldBe "Hello"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 5) shouldBe "Hello"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 6) shouldBe "SDLB!"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 10) shouldBe "SDLB!"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 11) shouldBe "SDLB!"
  }

  it should "retrieve correctly hovered word in simple case with multiple spaces" in {
    val simpleText = "Hello  SDLB!"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 0) shouldBe "Hello"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 4) shouldBe "Hello"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 5) shouldBe "Hello"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 6) shouldBe ""
    HoconParser.retrieveWordAtPosition(simpleText, 1, 7) shouldBe "SDLB!"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 11) shouldBe "SDLB!"
    HoconParser.retrieveWordAtPosition(simpleText, 1, 12) shouldBe "SDLB!"
  }

  it should "retrieve correctly hovered word in text" in {
    val fixture = loadFixture("fixture/hocon/with-comments-example.conf")
    val text = fixture.text
    HoconParser.retrieveWordAtPosition(text, 4, 0) shouldBe "global"
    HoconParser.retrieveWordAtPosition(text, 4, 6) shouldBe "global"
    HoconParser.retrieveWordAtPosition(text, 4, 7) shouldBe "{"
    HoconParser.retrieveWordAtPosition(text, 6, 7) shouldBe "#\"spark.sql.shuffle.partitions\"" // Which means hovering works with commented code
    HoconParser.retrieveWordAtPosition(text, 7, 7) shouldBe "\"spark.sql.shuffle.partitions\""
  }

  it should "transform line column position to absolute position" in {
    val fixture = loadFixture("fixture/hocon/with-lists-example.conf")
    val text = fixture.text

    HoconParser.lineColToAbsolutePosition(text, 1, 4) shouldBe 4
    HoconParser.lineColToAbsolutePosition(text, 1, 999) shouldBe 9
    HoconParser.lineColToAbsolutePosition(text, 3, 4) shouldBe 38
    HoconParser.lineColToAbsolutePosition(text, 3, 999) shouldBe 55
    HoconParser.lineColToAbsolutePosition(text, 17, 16) shouldBe 376
    HoconParser.lineColToAbsolutePosition(text, 17, 43) shouldBe 403
    HoconParser.lineColToAbsolutePosition(text, 19, 20) shouldBe 477
    HoconParser.lineColToAbsolutePosition(text, 30, 4) shouldBe 715
  }

  it should "find list areas with simple cases" in {
    val simpleText = "[]"

    HoconParser.findListAreaFrom(simpleText, 0) shouldBe None
    HoconParser.findListAreaFrom(simpleText, 1) shouldBe Some((1, 1))
    HoconParser.findListAreaFrom(simpleText, 2) shouldBe None

    val mediumText = "[[]]"
    HoconParser.findListAreaFrom(mediumText, 0) shouldBe None
    HoconParser.findListAreaFrom(mediumText, 1) shouldBe Some((1, 3))
    HoconParser.findListAreaFrom(mediumText, 2) shouldBe Some((2, 2))
    HoconParser.findListAreaFrom(mediumText, 3) shouldBe Some((1, 3))
    HoconParser.findListAreaFrom(mediumText, 4) shouldBe None

    val harderText = "[[ ]][   ]"
    HoconParser.findListAreaFrom(harderText, 0) shouldBe None
    HoconParser.findListAreaFrom(harderText, 1) shouldBe Some((1, 4))
    HoconParser.findListAreaFrom(harderText, 2) shouldBe Some((2, 3))
    HoconParser.findListAreaFrom(harderText, 3) shouldBe Some((2, 3))
    HoconParser.findListAreaFrom(harderText, 4) shouldBe Some((1, 4))
    HoconParser.findListAreaFrom(harderText, 5) shouldBe None
    HoconParser.findListAreaFrom(harderText, 6) shouldBe Some((6, 9))
    HoconParser.findListAreaFrom(harderText, 7) shouldBe Some((6, 9))
    HoconParser.findListAreaFrom(harderText, 8) shouldBe Some((6, 9))
    HoconParser.findListAreaFrom(harderText, 9) shouldBe Some((6, 9))
    HoconParser.findListAreaFrom(harderText, 10) shouldBe None
  }

  it should "find list areas with fixture" in {
    val fixture = loadFixture("fixture/hocon/with-lists-example.conf")
    val text = fixture.text


    HoconParser.findListAreaFrom(text, 376) shouldBe Some((376, 404)) // 376 == (17, 16), 403 == (17, 43)
    HoconParser.findListAreaFrom(text, 375) shouldBe None
    HoconParser.findListAreaFrom(text, 405) shouldBe None

    HoconParser.findListAreaFrom(text, 516) shouldBe Some((477, 715)) // 516 == (21, 6)

    HoconParser.findListAreaFrom(text, 38) shouldBe None
    HoconParser.findListAreaFrom(text, 716) shouldBe None

  }

  it should "find index of currentList" in {
    val fixture = loadFixture("fixture/hocon/with-lists-example.conf")
    val text = fixture.text

    HoconParser.findIndexIfInList(text, 1, 0) shouldBe None
    HoconParser.findIndexIfInList(text, 17, 20) shouldBe None
    HoconParser.findIndexIfInList(text, 22, 0) shouldBe Some(0)
    HoconParser.findIndexIfInList(text, 23, 8) shouldBe None
    HoconParser.findIndexIfInList(text, 27, 0) shouldBe Some(1)
    HoconParser.findIndexIfInList(text, 32, 0) shouldBe None

  }


  private def validateText(fixture: Fixture, column: Int, caretDataList: List[CaretData], positionMap: Option[List[(Int, Int)]]=None): Unit =
    val totalLines = fixture.originalText.count(_ == '\n') + 1
    for i <- 1 to totalLines do
      val lineNumber = positionMap.map(pm => pm(i-1)(0)).getOrElse(i)
      val columnNumber = positionMap.map(pm => pm(i-1)(1) + column).getOrElse(column)
      val (line, word) = HoconParser.retrieveDirectParent(fixture.text, lineNumber, columnNumber)
      val (pathList, _) = HoconParser.retrievePathList(fixture.config, line)
      val oIndex = HoconParser.findIndexIfInList(fixture.text, lineNumber, columnNumber)
      val caretData = CaretData(lineNumber, columnNumber, line, word, pathList, oIndex)
      caretData should be(caretDataList(i - 1))


  private def loadFixture(filePath: String): Fixture =
    val originalText = loadFile(filePath)
    val text = MultiLineTransformer.flattenMultiLines(originalText)
    Fixture(originalText, text, HoconParser.parse(text).get)

}
