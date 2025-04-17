package io.smartdatalake.context.hocon

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigRenderOptions, ConfigUtil}
import io.smartdatalake.UnitSpec
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

import scala.io.Source
import scala.util.Using

class HoconParserSpec extends UnitSpec {

  val (leftCol, rightCol) = (0, 999)
  case class CaretData(line: Int, column: Int, pathList: List[String])
  case class Fixture(originalText: String, text: String, config: Config)

  "Hocon parser" should "find path in hocon file" in {
    val fixture = loadFixture("fixture/hocon/basic-example.conf")

    val leftCaretData = List(
      CaretData(1, leftCol, List()),
      CaretData(2, leftCol, List("global")),
      CaretData(3, leftCol, List("global", "spark-options")),
      CaretData(4, leftCol, List("global", "spark-options")),
      CaretData(5, leftCol, List("global"))
    )

    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, List("global")),
      CaretData(2, rightCol, List("global", "spark-options")),
      CaretData(3, rightCol, List("global", "spark-options", "\"spark.sql.shuffle.partitions\"")),
      CaretData(4, rightCol, List("global")),
      CaretData(5, rightCol, List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "find path in file with comments" in {
    val fixture = loadFixture("fixture/hocon/with-comments-example.conf")

    val leftCaretData = List(
      CaretData(1, leftCol, List()),
      CaretData(2, leftCol, List()),
      CaretData(3, leftCol, List()),
      CaretData(4, leftCol, List()),
      CaretData(5, leftCol, List("global")),
      CaretData(6, leftCol, List("global", "spark-options")),
      CaretData(7, leftCol, List("global", "spark-options")),
      CaretData(8, leftCol, List("global", "spark-options")),
      CaretData(9, leftCol, List("global", "spark-options")),
      CaretData(10, leftCol, List("global"))
    )
    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, List()),
      CaretData(2, rightCol, List()),
      CaretData(3, rightCol, List()),
      CaretData(4, rightCol, List("global")),
      CaretData(5, rightCol, List("global", "spark-options")),
      CaretData(6, rightCol, List("global", "spark-options")),
      CaretData(7, rightCol, List("global", "spark-options", "\"spark.sql.shuffle.partitions\"")),
      CaretData(8, rightCol, List("global", "spark-options")),
      CaretData(9, rightCol, List("global")),
      CaretData(10, rightCol, List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "find path in with multi-line values" in {
    val fixture = loadFixture("fixture/hocon/with-multi-lines-example.conf")
    val positionMap = MultiLineTransformer.computeCorrectedPositions(fixture.originalText)

    val leftCaretData = List(
      CaretData(positionMap( 0)(0), positionMap( 0)(1) + leftCol, List()),
      CaretData(positionMap( 1)(0), positionMap( 1)(1) + leftCol, List("actions")),
      CaretData(positionMap( 2)(0), positionMap( 2)(1) + leftCol, List("actions")),
      CaretData(positionMap( 3)(0), positionMap( 3)(1) + leftCol, List("actions", "join-departures-airports")),
      CaretData(positionMap( 4)(0), positionMap( 4)(1) + leftCol, List("actions", "join-departures-airports")),
      CaretData(positionMap( 5)(0), positionMap( 5)(1) + leftCol, List("actions", "join-departures-airports")),
      CaretData(positionMap( 6)(0), positionMap( 6)(1) + leftCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap( 7)(0), positionMap( 7)(1) + leftCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap( 8)(0), positionMap( 8)(1) + leftCol, List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(positionMap( 9)(0), positionMap( 9)(1) + leftCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(10)(0), positionMap(10)(1) + leftCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(11)(0), positionMap(11)(1) + leftCol, List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(positionMap(12)(0), positionMap(12)(1) + leftCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap(13)(0), positionMap(13)(1) + leftCol, List("actions", "join-departures-airports")),
      CaretData(positionMap(14)(0), positionMap(14)(1) + leftCol, List("actions")),
      CaretData(positionMap(15)(0), positionMap(15)(1) + leftCol, List("actions")),
      CaretData(positionMap(16)(0), positionMap(16)(1) + leftCol, List("actions", "compute-distances")),
      CaretData(positionMap(17)(0), positionMap(17)(1) + leftCol, List("actions", "compute-distances")),
      CaretData(positionMap(18)(0), positionMap(18)(1) + leftCol, List("actions", "compute-distances", "code")),
      CaretData(positionMap(19)(0), positionMap(19)(1) + leftCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(20)(0), positionMap(20)(1) + leftCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(21)(0), positionMap(21)(1) + leftCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(22)(0), positionMap(22)(1) + leftCol, List("actions", "compute-distances", "code")),
      CaretData(positionMap(23)(0), positionMap(23)(1) + leftCol, List("actions", "compute-distances")),
      CaretData(positionMap(24)(0), positionMap(24)(1) + leftCol, List("actions", "compute-distances", "metadata")),
      CaretData(positionMap(25)(0), positionMap(25)(1) + leftCol, List("actions", "compute-distances", "metadata")),
      CaretData(positionMap(26)(0), positionMap(26)(1) + leftCol, List("actions", "compute-distances")),
      CaretData(positionMap(27)(0), positionMap(27)(1) + leftCol, List("actions"))
    )

    validateText(fixture, leftCol, leftCaretData, positionMap=Some(positionMap))

    val rightCaretData = List(
      CaretData(positionMap( 0)(0), positionMap( 0)(1) + rightCol, List("actions")),
      CaretData(positionMap( 1)(0), positionMap( 1)(1) + rightCol, List("actions")),
      CaretData(positionMap( 2)(0), positionMap( 2)(1) + rightCol, List("actions", "join-departures-airports")),
      CaretData(positionMap( 3)(0), positionMap( 3)(1) + rightCol, List("actions", "join-departures-airports", "type")),
      CaretData(positionMap( 4)(0), positionMap( 4)(1) + rightCol, List("actions", "join-departures-airports", "inputIds")),
      CaretData(positionMap( 5)(0), positionMap( 5)(1) + rightCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap( 6)(0), positionMap( 6)(1) + rightCol, List("actions", "join-departures-airports", "transformer", "className")),
      CaretData(positionMap( 7)(0), positionMap( 7)(1) + rightCol, List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(positionMap( 8)(0), positionMap( 8)(1) + rightCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap( 9)(0), positionMap( 9)(1) + rightCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(10)(0), positionMap(10)(1) + rightCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(positionMap(11)(0), positionMap(11)(1) + rightCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(positionMap(12)(0), positionMap(12)(1) + rightCol, List("actions", "join-departures-airports")),
      CaretData(positionMap(13)(0), positionMap(13)(1) + rightCol, List("actions")),
      CaretData(positionMap(14)(0), positionMap(14)(1) + rightCol, List("actions")),
      CaretData(positionMap(15)(0), positionMap(15)(1) + rightCol, List("actions", "compute-distances")),
      CaretData(positionMap(16)(0), positionMap(16)(1) + rightCol, List("actions", "compute-distances", "type")),
      CaretData(positionMap(17)(0), positionMap(17)(1) + rightCol, List("actions", "compute-distances", "code")),
      CaretData(positionMap(18)(0), positionMap(18)(1) + rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(19)(0), positionMap(19)(1) + rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(20)(0), positionMap(20)(1) + rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(21)(0), positionMap(21)(1) + rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(positionMap(22)(0), positionMap(22)(1) + rightCol, List("actions", "compute-distances")),
      CaretData(positionMap(23)(0), positionMap(23)(1) + rightCol, List("actions", "compute-distances", "metadata")),
      CaretData(positionMap(24)(0), positionMap(24)(1) + rightCol, List("actions", "compute-distances", "metadata", "feed")),
      CaretData(positionMap(25)(0), positionMap(25)(1) + rightCol, List("actions", "compute-distances")),
      CaretData(positionMap(26)(0), positionMap(26)(1) + rightCol, List("actions")),
      CaretData(positionMap(27)(0), positionMap(27)(1) + rightCol, List())
    )
    validateText(fixture, rightCol, rightCaretData, positionMap=Some(positionMap))

  }


  it should "find path in file with lists" in {
    val fixture = loadFixture("fixture/hocon/with-lists-example.conf")
    
    val leftCaretData = List(
      CaretData(1, leftCol, List()),
      CaretData(2, leftCol, List("actions")),
      CaretData(3, leftCol, List("actions", "select-airport-cols")),
      CaretData(4, leftCol, List("actions", "select-airport-cols")),
      CaretData(5, leftCol, List("actions", "select-airport-cols")),
      CaretData(6, leftCol, List("actions", "select-airport-cols")),
      CaretData(7, leftCol, List("actions", "select-airport-cols", "transformers", "0")),
      CaretData(8, leftCol, List("actions", "select-airport-cols", "transformers", "0")),
      CaretData(9, leftCol, List("actions", "select-airport-cols", "transformers", "0")),
      CaretData(10, leftCol, List("actions", "select-airport-cols")),
      CaretData(11, leftCol, List("actions", "select-airport-cols", "metadata")),
      CaretData(12, leftCol, List("actions", "select-airport-cols", "metadata")),
      CaretData(13, leftCol, List("actions", "select-airport-cols")),
      CaretData(14, leftCol, List("actions")),
      CaretData(15, leftCol, List("actions")),
      CaretData(16, leftCol, List("actions", "join-departures-airports")),
      CaretData(17, leftCol, List("actions", "join-departures-airports")),
      CaretData(18, leftCol, List("actions", "join-departures-airports")),
      CaretData(19, leftCol, List("actions", "join-departures-airports")),
      CaretData(20, leftCol, List("actions", "join-departures-airports", "transformers", "0")),
      CaretData(21, leftCol, List("actions", "join-departures-airports", "transformers", "0")),
      CaretData(22, leftCol, List("actions", "join-departures-airports", "transformers", "0", "code")),
      CaretData(23, leftCol, List("actions", "join-departures-airports", "transformers", "0", "code")),
      CaretData(24, leftCol, List("actions", "join-departures-airports", "transformers")),
      CaretData(25, leftCol, List("actions", "join-departures-airports", "transformers", "1")),
      CaretData(26, leftCol, List("actions", "join-departures-airports", "transformers", "1")),
      CaretData(27, leftCol, List("actions", "join-departures-airports", "transformers", "1", "code")),
      CaretData(28, leftCol, List("actions", "join-departures-airports", "transformers", "1", "code")),
      CaretData(29, leftCol, List("actions", "join-departures-airports", "transformers", "1")),
      CaretData(30, leftCol, List("actions", "join-departures-airports", "transformers")),
      CaretData(31, leftCol, List("actions", "join-departures-airports")),
      CaretData(32, leftCol, List("actions", "join-departures-airports", "metadata")),
      CaretData(33, leftCol, List("actions", "join-departures-airports", "metadata")),
      CaretData(34, leftCol, List("actions", "join-departures-airports")),
      CaretData(35, leftCol, List("actions", "join-departures-airports", "executionMode")),
      CaretData(36, leftCol, List("actions", "join-departures-airports", "executionMode")),
      CaretData(37, leftCol, List("actions", "join-departures-airports", "executionMode")),
      CaretData(38, leftCol, List("actions", "join-departures-airports")),
      CaretData(39, leftCol, List("actions"))
    )

    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, List("actions")),
      CaretData(2, rightCol, List("actions", "select-airport-cols")),
      CaretData(3, rightCol, List("actions", "select-airport-cols", "type")),
      CaretData(4, rightCol, List("actions", "select-airport-cols", "inputId")),
      CaretData(5, rightCol, List("actions", "select-airport-cols", "outputId")),
      CaretData(6, rightCol, List("actions", "select-airport-cols", "transformers", "0")),
      CaretData(7, rightCol, List("actions", "select-airport-cols", "transformers", "0", "type")),
      CaretData(8, rightCol, List("actions", "select-airport-cols", "transformers", "0", "code")),
      CaretData(9, rightCol, List("actions", "select-airport-cols")),
      CaretData(10, rightCol, List("actions", "select-airport-cols", "metadata")),
      CaretData(11, rightCol, List("actions", "select-airport-cols", "metadata", "feed")),
      CaretData(12, rightCol, List("actions", "select-airport-cols")),
      CaretData(13, rightCol, List("actions")),
      CaretData(14, rightCol, List("actions")),
      CaretData(15, rightCol, List("actions", "join-departures-airports")),
      CaretData(16, rightCol, List("actions", "join-departures-airports", "type")),
      CaretData(17, rightCol, List("actions", "join-departures-airports", "inputIds")),
      CaretData(18, rightCol, List("actions", "join-departures-airports", "outputIds")),
      CaretData(19, rightCol, List("actions", "join-departures-airports", "transformers", "0")),
      CaretData(20, rightCol, List("actions", "join-departures-airports", "transformers", "0", "type")),
      CaretData(21, rightCol, List("actions", "join-departures-airports", "transformers", "0", "code")),
      CaretData(22, rightCol, List("actions", "join-departures-airports", "transformers", "0", "code", "btl-connected-airports")),
      CaretData(23, rightCol, List("actions", "join-departures-airports", "transformers")),
      CaretData(24, rightCol, List("actions", "join-departures-airports", "transformers", "1")),
      CaretData(25, rightCol, List("actions", "join-departures-airports", "transformers", "1", "type")),
      CaretData(26, rightCol, List("actions", "join-departures-airports", "transformers", "1", "code")),
      CaretData(27, rightCol, List("actions", "join-departures-airports", "transformers", "1", "code", "btl-departures-arrivals-airports")),
      CaretData(28, rightCol, List("actions", "join-departures-airports", "transformers", "1")),
      CaretData(29, rightCol, List("actions", "join-departures-airports", "transformers")),
      CaretData(30, rightCol, List("actions", "join-departures-airports")),
      CaretData(31, rightCol, List("actions", "join-departures-airports", "metadata")),
      CaretData(32, rightCol, List("actions", "join-departures-airports", "metadata", "feed")),
      CaretData(33, rightCol, List("actions", "join-departures-airports")),
      CaretData(34, rightCol,List("actions", "join-departures-airports", "executionMode")),
      CaretData(35, rightCol, List("actions", "join-departures-airports", "executionMode", "type")),
      CaretData(36, rightCol, List("actions", "join-departures-airports", "executionMode", "className")),
      CaretData(37, rightCol, List("actions", "join-departures-airports")),
      CaretData(38, rightCol, List("actions")),
      CaretData(39, rightCol, List())
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

    HoconParser.lineColToAbsolutePosition(text, 0, 4) shouldBe 4
    HoconParser.lineColToAbsolutePosition(text, 0, 999) shouldBe 9
    HoconParser.lineColToAbsolutePosition(text, 2, 4) shouldBe 38
    HoconParser.lineColToAbsolutePosition(text, 2, 999) shouldBe 55
    HoconParser.lineColToAbsolutePosition(text, 16, 16) shouldBe 376
    HoconParser.lineColToAbsolutePosition(text, 16, 43) shouldBe 403
    HoconParser.lineColToAbsolutePosition(text, 18, 20) shouldBe 477
    HoconParser.lineColToAbsolutePosition(text, 29, 4) shouldBe 715
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

    HoconParser.findIndexIfInList(text, 0, 0) shouldBe None
    HoconParser.findIndexIfInList(text, 16, 20) shouldBe None
    HoconParser.findIndexIfInList(text, 21, 0) shouldBe Some(0)
    HoconParser.findIndexIfInList(text, 22, 8) shouldBe None
    HoconParser.findIndexIfInList(text, 26, 0) shouldBe Some(1)
    HoconParser.findIndexIfInList(text, 31, 0) shouldBe None

  }

  it should "suport new way of retrieving path" in {
    import scala.annotation.tailrec

    object Token:
      val KeyValSplitRegex = "[\\[{=]"
      val StartObject = '{'
      val EndObject = '}'
      val StartList = '['
      val EndList = ']'
      val NewLinePattern = "\\R"
      val Comment = '#'

    type Position = (Int, Int)
    type ParentInfo = (Position, String)

    def retrievePath(text: String, line: Int, col: Int): List[String] =
      @tailrec
      def retrievePathHelper(line: Int, col: Int, acc: List[String]): List[String] =
        val ((parentLine, parentCol), parentName) = retrieveDirectParent(text, line, col)
        val indexIfInList = findIndexIfInList(text, line, col)
        if parentName.isEmpty then acc
        else
          val newAcc = indexIfInList match
            case Some(idx) if idx >= 0 && isParentOutsideArray(text, line, col, parentLine, parentCol) => parentName :: idx.toString :: acc
            case _ => parentName :: acc
          retrievePathHelper(parentLine, parentCol, newAcc)

      retrievePathHelper(line-1, col, List.empty) // Line is 1-based

    def isParentOutsideArray(text: String, line: Int, col: Int, parentLine: Int, parentCol: Int): Boolean =
      val absoluteParentPosition = lineColToAbsolutePosition(text, parentLine, parentCol)
      val absolutePosition = lineColToAbsolutePosition(text, line, col)
      findListAreaFrom(text, absolutePosition).map(_._1  > absoluteParentPosition).getOrElse(false)

    /**
     * Retrieves the direct parent element at the specified position
     */
    def retrieveDirectParent(text: String, line: Int, column: Int): ParentInfo =
      val lines = text.split(Token.NewLinePattern, -1)
      val textLine = if line >= 0 && line < lines.length then
        lines(line).takeWhile(_ != Token.Comment).mkString
      else 
        ""
      val col = math.min(textLine.length, column)
      
      //if hasClosingStructure(textLine, col) then
      //  retrieveParentRecursively(text, line, col)
      //else
      val keyValSplit = textLine.split(Token.KeyValSplitRegex)
      if keyValSplit.nonEmpty && col > keyValSplit(0).length then
        val keyName = keyValSplit(0).trim
        if keyName.isEmpty then
          retrieveParentRecursively(text, line, col)
          //findValidDirectParentName(text, line-1).getOrElse(
          //  retrieveParentRecursively(text, line, col)
          //)
        else
          val colPos = textLine.indexOf(keyName)
          ((line, colPos), keyName)
      else
        retrieveParentRecursively(text, line, col)

    def findIndexIfInList(text: String, line: Int, column: Int): Option[Int] =
      val absolutePosition = lineColToAbsolutePosition(text, line, column)
      findListAreaFrom(text, absolutePosition) match
        case None => None
        case Some((startPosition, endPosition)) =>
          @tailrec
          def buildObjectPositions(currentPosition: Int, currentList: List[(Int, Int)]): List[(Int, Int)] =
            val nextStartObjectTokenRelativePosition = text.substring(currentPosition).indexOf(Token.StartObject)
            if nextStartObjectTokenRelativePosition == -1 then
              currentList
            else
              val nextStartObjectTokenAbsolutePosition = nextStartObjectTokenRelativePosition + currentPosition
              if nextStartObjectTokenAbsolutePosition > endPosition then
                currentList
              else
                findObjectAreaFrom(text, nextStartObjectTokenAbsolutePosition + 1) match // +1 to enter in the object
                  case None => currentList
                  case Some((start, end)) => buildObjectPositions(end + 1, (start, end)::currentList)

          val objectPositions = buildObjectPositions(startPosition, List.empty[(Int, Int)]).reverse
          objectPositions
            .zipWithIndex
            .find{(bounds, _) => absolutePosition >= bounds(0) && absolutePosition <= bounds(1)}
            .map(_._2)





    def findObjectAreaFrom(text: String, position: Int): Option[(Int, Int)] = findAreaFrom(text, position, Token.StartObject, Token.EndObject)
    def findListAreaFrom(text: String, position: Int): Option[(Int, Int)] = findAreaFrom(text, position, Token.StartList, Token.EndList)
    

    def findAreaFrom(text: String, position: Int, startToken: Char, endToken: Char): Option[(Int, Int)] =
      @tailrec
      def indexOfWithDepth(char: Char, oppositeChar: Char, position: Int, direction: 1 | -1, depth: Int=0): Int =
        assert(depth >= 0)
        if position < 0 || position == text.length then -1 else text(position) match
        case c if c == char && depth == 0 => position
        case c if c == char               => indexOfWithDepth(char, oppositeChar, position + direction, direction, depth - 1)
        case c if c == oppositeChar       => indexOfWithDepth(char, oppositeChar, position + direction, direction, depth + 1)
        case _                            => indexOfWithDepth(char, oppositeChar, position + direction, direction, depth)

      val startPosition = indexOfWithDepth(startToken, endToken, position-1, -1)
      val endPosition = indexOfWithDepth(endToken, startToken, position, 1)
      if startPosition != -1 && endPosition != -1 then Some((startPosition+1, endPosition)) else None // (...+1, ...-1) to exclude list characters themselves

    def lineColToAbsolutePosition(text: String, line: Int, column: Int): Int =
      val textLine = text.split(Token.NewLinePattern, -1)
      val nCharactersBeforeCurrentLine = textLine.take(line).map(line => line.length + 1).sum // +1 for \n character
      val nCharactersCurrentLine = math.min(textLine(line).length, column)
      nCharactersCurrentLine + nCharactersBeforeCurrentLine


    

    /**
     * Checks if the text line has more closing than opening structures after the column position
     */
    //def hasClosingStructure(textLine: String, col: Int): Boolean =
    //  textLine.count(_ == Token.EndObject) + textLine.count(_ == Token.EndList) > 
    //  textLine.count(_ == Token.StartObject) + textLine.count(_ == Token.StartList)

    /**
     * Finds a valid parent name by looking upward in the text
     */
    /* def findValidDirectParentName(text: String, line: Int): Option[ParentInfo] =
      val lines = text.split(Token.NewLinePattern, -1)
      
      @tailrec
      def helper(line: Int): Option[ParentInfo] = 
        if line <= 0 then None 
        else
          val origLine = lines(line-1)
          val textLine = origLine.takeWhile(_ != Token.Comment).mkString
          
          if textLine.isBlank then 
            helper(line-1) 
          else
            val words = textLine.filterNot(c => 
              c == Token.StartList || c == Token.EndList || 
              c == Token.StartObject || c == Token.EndObject || 
              c == '=' || c == '"').split(" ")
            
            words match
              case Array(singleBlock) => 
                val colPos = origLine.indexOf(singleBlock.trim)
                Some(((line, colPos), singleBlock))
              case _ => None
      
      helper(line) */

    /**
     * Recursively retrieves a parent by traversing upward through the text
     */
    def retrieveParentRecursively(text: String, line: Int, col: Int): ParentInfo =
      val lines = text.split(Token.NewLinePattern, -1)
      
      @tailrec
      def retrieveHelper(line: Int, depth: Int): ParentInfo =
        if line < 0 then
          ((0, 0), "")
        else
          val origLine = lines(line)
          val textLine = origLine.filterNot(c => c.isWhitespace).takeWhile(_ != Token.Comment).mkString
          
          // Update depth
          val newDepth = depth + computeDepth(textLine)         
          
          if textLine.contains(Token.EndObject) || textLine.contains(Token.EndList) then 
            retrieveHelper(line-1, newDepth) 
          else
            textLine.split(Token.KeyValSplitRegex) match
              case Array(singleBlock) => 
                if singleBlock.isBlank then 
                  retrieveHelper(line-1, newDepth) 
                else if newDepth < 0 then 
                  // Calculate the column for the parent block
                  val colPos = origLine.indexOf(singleBlock.trim)
                  
                  ((line, colPos), singleBlock)
                else retrieveHelper(line-1, newDepth)
              case _ => retrieveHelper(line-1, newDepth)
      
      def computeDepth(textLine: String): Int =
        val startObjects = textLine.count(_ == Token.StartObject)
        val endObjects = textLine.count(_ == Token.EndObject)
        val startLists = textLine.count(_ == Token.StartList)
        val endLists = textLine.count(_ == Token.EndList)
        endObjects - startObjects + endLists - startLists
      
      val currentDepth = computeDepth(lines(line).take(col))
      retrieveHelper(line-1, currentDepth)

    val config = """
      application {
        name = "example-app"
        database {
          url = "jdbc:postgresql://localhost/mydb"
          user = "admin"
          password = "secret"
        }
        features = [
          {
            name = "feature1"
            enabled = true
          },
          {
            name = "feature2"
            enabled = false
          }
        ]
      }
    """

    retrievePath(config, 1, 0) shouldBe List()
    retrievePath(config, 1, 999) shouldBe List()

    retrievePath(config, 2, 8) shouldBe List()
    retrievePath(config, 2, 999) shouldBe List("application")

    retrievePath(config, 3, 6) shouldBe List("application")
    retrievePath(config, 3, 16) shouldBe List("application", "name")

    retrievePath(config, 4, 0) shouldBe List("application")
    retrievePath(config, 4, 999) shouldBe List("application", "database")

    retrievePath(config, 5, 0) shouldBe List("application", "database")
    retrievePath(config, 5, 999) shouldBe List("application", "database", "url")

    retrievePath(config, 6, 0) shouldBe List("application", "database")
    retrievePath(config, 6, 999) shouldBe List("application", "database", "user")

    retrievePath(config, 7, 0) shouldBe List("application", "database")
    retrievePath(config, 7, 999) shouldBe List("application", "database", "password")

    retrievePath(config, 8, 0) shouldBe List("application", "database")
    retrievePath(config, 8, 999) shouldBe List("application")

    retrievePath(config, 9, 0) shouldBe List("application")
    retrievePath(config, 9, 999) shouldBe List("application", "features")

    retrievePath(config, 10, 0) shouldBe List("application", "features")
    retrievePath(config, 10, 999) shouldBe List("application", "features", "0")

    retrievePath(config, 11, 0) shouldBe List("application", "features", "0")
    retrievePath(config, 11, 999) shouldBe List("application", "features", "0", "name")

    retrievePath(config, 12, 0) shouldBe List("application", "features", "0")
    retrievePath(config, 12, 999) shouldBe List("application", "features", "0", "enabled")

    retrievePath(config, 13, 0) shouldBe List("application", "features", "0")
    retrievePath(config, 13, 999) shouldBe List("application", "features")

    retrievePath(config, 14, 0) shouldBe List("application", "features")
    retrievePath(config, 14, 999) shouldBe List("application", "features", "1")

    retrievePath(config, 15, 0) shouldBe List("application", "features", "1")
    retrievePath(config, 15, 999) shouldBe List("application", "features", "1", "name")

    retrievePath(config, 16, 0) shouldBe List("application", "features", "1")
    retrievePath(config, 16, 999) shouldBe List("application", "features", "1", "enabled")

    retrievePath(config, 17, 0) shouldBe List("application", "features", "1")
    retrievePath(config, 17, 999) shouldBe List("application", "features")

    retrievePath(config, 18, 0) shouldBe List("application", "features")
    retrievePath(config, 18, 999)shouldBe List("application")

    retrievePath(config, 19, 0) shouldBe List("application")
    retrievePath(config, 19, 999) shouldBe List()

  }


  private def validateText(fixture: Fixture, column: Int, caretDataList: List[CaretData], positionMap: Option[List[(Int, Int)]]=None): Unit =
    val totalLines = fixture.originalText.count(_ == '\n') + 1
    for i <- 1 to totalLines do
      val lineNumber = positionMap.map(pm => pm(i-1)(0)).getOrElse(i)
      val columnNumber = positionMap.map(pm => pm(i-1)(1) + column).getOrElse(column)
      val pathList = HoconParser.retrieveParentPath(fixture.text, lineNumber, columnNumber)
      val caretData = CaretData(lineNumber, columnNumber, pathList)
      caretData should be(caretDataList(i - 1))


  private def loadFixture(filePath: String): Fixture =
    val originalText = loadFile(filePath)
    val text = MultiLineTransformer.flattenMultiLines(originalText)
    Fixture(originalText, text, HoconParser.parse(text).get)

}
