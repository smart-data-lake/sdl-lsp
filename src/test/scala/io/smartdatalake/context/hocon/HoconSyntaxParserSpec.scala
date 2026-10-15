package io.smartdatalake.context.hocon

import io.smartdatalake.UnitSpec


class HoconSyntaxParserSpec extends UnitSpec {

  val (leftCol, rightCol) = (0, 999)
  case class CaretData(line: Int, column: Int, pathList: List[String])

  "Hocon syntax parser" should "find path in hocon file" in {
    val fixture = loadFile("fixture/hocon/basic-example.conf")

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
      CaretData(3, rightCol, List("global", "spark-options", "spark.sql.shuffle.partitions")),
      CaretData(4, rightCol, List("global")),
      CaretData(5, rightCol, List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "find path in file with comments" in {
    val fixture = loadFile("fixture/hocon/with-comments-example.conf")

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
      CaretData(7, rightCol, List("global", "spark-options", "spark.sql.shuffle.partitions")),
      CaretData(8, rightCol, List("global", "spark-options")),
      CaretData(9, rightCol, List("global")),
      CaretData(10, rightCol, List())
    )

    validateText(fixture, rightCol, rightCaretData)

  }

  it should "find path with multi-line values" in {
    val fixture = loadFile("fixture/hocon/with-multi-lines-example.conf")

    val leftCaretData = List(
      CaretData(1, leftCol, List()),
      CaretData(2, leftCol, List("actions")),
      CaretData(3, leftCol, List("actions")),
      CaretData(4, leftCol, List("actions", "join-departures-airports")),
      CaretData(5, leftCol, List("actions", "join-departures-airports")),
      CaretData(6, leftCol, List("actions", "join-departures-airports")),
      CaretData(7, leftCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(8, leftCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(9, leftCol, List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(10, leftCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(11, leftCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(12, leftCol, List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(13, leftCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(14, leftCol, List("actions", "join-departures-airports")),
      CaretData(15, leftCol, List("actions")),
      CaretData(16, leftCol, List("actions")),
      CaretData(17, leftCol, List("actions", "compute-distances")),
      CaretData(18, leftCol, List("actions", "compute-distances")),
      CaretData(19, leftCol, List("actions", "compute-distances", "code")),
      CaretData(20, leftCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(21, leftCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(22, leftCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(23, leftCol, List("actions", "compute-distances", "code")),
      CaretData(24, leftCol, List("actions", "compute-distances")),
      CaretData(25, leftCol, List("actions", "compute-distances", "metadata")),
      CaretData(26, leftCol, List("actions", "compute-distances", "metadata")),
      CaretData(27, leftCol, List("actions", "compute-distances")),
      CaretData(28, leftCol, List("actions"))
    )

    validateText(fixture, leftCol, leftCaretData)

    val rightCaretData = List(
      CaretData(1, rightCol, List("actions")),
      CaretData(2, rightCol, List("actions")),
      CaretData(3, rightCol, List("actions", "join-departures-airports")),
      CaretData(4, rightCol, List("actions", "join-departures-airports", "type")),
      CaretData(5, rightCol, List("actions", "join-departures-airports")), // after the closing bracket
      CaretData(6, rightCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(7, rightCol, List("actions", "join-departures-airports", "transformer", "className")),
      CaretData(8, rightCol, List("actions", "join-departures-airports", "transformer", "code")),
      CaretData(9, rightCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(10, rightCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(11, rightCol, List("actions", "join-departures-airports", "transformer", "code", "btl-connected-airports")),
      CaretData(12, rightCol, List("actions", "join-departures-airports", "transformer")),
      CaretData(13, rightCol, List("actions", "join-departures-airports")),
      CaretData(14, rightCol, List("actions")),
      CaretData(15, rightCol, List("actions")),
      CaretData(16, rightCol, List("actions", "compute-distances")),
      CaretData(17, rightCol, List("actions", "compute-distances", "type")),
      CaretData(18, rightCol, List("actions", "compute-distances", "code")),
      CaretData(19, rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(20, rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(21, rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(22, rightCol, List("actions", "compute-distances", "code", "btl-departures-arrivals-airports")),
      CaretData(23, rightCol, List("actions", "compute-distances")),
      CaretData(24, rightCol, List("actions", "compute-distances", "metadata")),
      CaretData(25, rightCol, List("actions", "compute-distances", "metadata", "feed")),
      CaretData(26, rightCol, List("actions", "compute-distances")),
      CaretData(27, rightCol, List("actions")),
      CaretData(28, rightCol, List())
    )
    validateText(fixture, rightCol, rightCaretData)

  }


  it should "find path in file with lists" in {
    val fixture = loadFile("fixture/hocon/with-lists-example.conf")
    
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
      CaretData(17, rightCol, List("actions", "join-departures-airports")), // after the closing bracket
      CaretData(18, rightCol, List("actions", "join-departures-airports")),
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

  it should "find path in a sample with lists of objects" in {
    val text = """
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
    def path(line: Int, col: Int) = pathAt(text, line, col)

    path(1, 0) shouldBe List()
    path(1, 999) shouldBe List()
    path(2, 8) shouldBe List()
    path(2, 999) shouldBe List("application")
    path(3, 6) shouldBe List("application")
    path(3, 16) shouldBe List("application", "name")
    path(4, 0) shouldBe List("application")
    path(4, 999) shouldBe List("application", "database")
    path(5, 0) shouldBe List("application", "database")
    path(5, 999) shouldBe List("application", "database", "url")
    path(8, 0) shouldBe List("application", "database")
    path(8, 999) shouldBe List("application")
    path(9, 0) shouldBe List("application")
    path(9, 999) shouldBe List("application", "features")
    path(10, 0) shouldBe List("application", "features")
    path(10, 999) shouldBe List("application", "features", "0")
    path(11, 999) shouldBe List("application", "features", "0", "name")
    path(13, 0) shouldBe List("application", "features", "0")
    path(13, 999) shouldBe List("application", "features")
    path(14, 999) shouldBe List("application", "features", "1")
    path(16, 999) shouldBe List("application", "features", "1", "enabled")
    path(17, 999) shouldBe List("application", "features")
    path(18, 0) shouldBe List("application", "features")
    path(18, 999) shouldBe List("application")
    path(19, 999) shouldBe List()
  }

  it should "find path when the brace is on the line after the key" in {
    val text = "actions {\n  a1\n  {\n    inputId = \n  }\n}"
    pathAt(text, 4, 14) shouldBe List("actions", "a1", "inputId")
    pathAt(text, 4, 0) shouldBe List("actions", "a1")
    pathAt(text, 2, 4) shouldBe List("actions")
    HoconSyntaxParser.parse(text).errors.map(_.message) shouldBe List("Missing value")
  }

  it should "split dotted keys into a path" in {
    val text = "actions.a1.metadata {\n  feed = x\n}\ndataObjects.obj1.type = CsvFileDataObject"
    pathAt(text, 2, 10) shouldBe List("actions", "a1", "metadata", "feed")
    pathAt(text, 2, 0) shouldBe List("actions", "a1", "metadata")
    pathAt(text, 4, 999) shouldBe List("dataObjects", "obj1", "type")
    pathAt(text, 4, 14) shouldBe List("dataObjects") // in the key segment obj1
    wordAt(text, 4, 14) shouldBe "obj1"
    wordAt(text, 4, 18) shouldBe "type"
    pathAt("\"a.b\" = 1", 1, 999) shouldBe List("a.b")
    HoconSyntaxParser.parse("a.\"b.c\".d = 1").root.fields.head.key shouldBe List(KeySegment("a", 0, 1), KeySegment("b.c", 3, 6), KeySegment("d", 8, 9))
  }

  it should "support += and colons" in {
    pathAt("a {\n  inputIds += \n}", 2, 14) shouldBe List("a", "inputIds")
    pathAt("a: {\n  b: x\n}", 2, 6) shouldBe List("a", "b")
  }

  it should "find path with several fields on one line" in {
    val text = "actions {\n  a1 { type = CopyAction, inputId = obj, metadata { feed = f } }\n}"
    pathAt(text, 2, 23) shouldBe List("actions", "a1", "type")
    pathAt(text, 2, 36) shouldBe List("actions", "a1", "inputId")
    pathAt(text, 2, 40) shouldBe List("actions", "a1")
    pathAt(text, 2, 60) shouldBe List("actions", "a1", "metadata", "feed")
    pathAt(text, 2, 62) shouldBe List("actions", "a1")
    pathAt(text, 2, 64) shouldBe List("actions")
  }

  it should "find path in objects in lists on one line" in {
    val text = "a {\n  t = [{ x = 1 }, { y = 2 }]\n}"
    pathAt(text, 2, 9) shouldBe List("a", "t", "0")
    pathAt(text, 2, 12) shouldBe List("a", "t", "0", "x")
    pathAt(text, 2, 18) shouldBe List("a", "t")
    pathAt(text, 2, 26) shouldBe List("a", "t", "1", "y")
    pathAt(text, 2, 27) shouldBe List("a", "t")
    pathAt(text, 2, 28) shouldBe List("a") // after the closing bracket
  }

  it should "find words in lists without spaces" in {
    val text = "a {\n  inputIds = [obj1,obj2]\n}"
    wordAt(text, 2, 14) shouldBe "obj1"
    wordAt(text, 2, 18) shouldBe "obj1"
    wordAt(text, 2, 19) shouldBe "obj2"
    wordAt(text, 2, 23) shouldBe "obj2"
    pathAt(text, 2, 19) shouldBe List("a", "inputIds")
  }

  it should "ignore structure in strings and comments" in {
    val text = "a {\n  sql = \"\"\"select '}'\n  from { t\"\"\"\n  url = \"http://x/#y\" # b {\n  // c {\n  \n}"
    pathAt(text, 3, 4) shouldBe List("a", "sql")
    pathAt(text, 4, 999) shouldBe List("a", "url")
    pathAt(text, 6, 2) shouldBe List("a")
    wordAt(text, 5, 6) shouldBe ""
    HoconSyntaxParser.parse(text).errors shouldBe empty
  }

  it should "retrieve correctly hovered word in simple case" in {
    def word(col: Int) = wordAt("Hello SDLB!", 1, col)
    List(0, 4, 5).map(word) shouldBe List("Hello", "Hello", "Hello")
    List(6, 10, 11).map(word) shouldBe List("SDLB!", "SDLB!", "SDLB!")
    wordAt("Hello  SDLB!", 1, 6) shouldBe ""
    wordAt("Hello  SDLB!", 1, 7) shouldBe "SDLB!"
  }

  it should "retrieve correctly hovered word in text" in {
    val text = loadFile("fixture/hocon/with-comments-example.conf")
    wordAt(text, 4, 0) shouldBe "global"
    wordAt(text, 4, 6) shouldBe "global"
    wordAt(text, 4, 7) shouldBe "{"
    wordAt(text, 6, 7) shouldBe "" // in a comment
    wordAt(text, 7, 7) shouldBe "spark.sql.shuffle.partitions"
  }

  it should "accept text in the middle of editing" in {
    val missingValue = HoconSyntaxParser.parse("actions {\n  a1 {\n    inputId = \n    type = CopyAction\n  }\n}")
    missingValue.errors.map(_.message) shouldBe List("Missing value")
    pathAt("actions {\n  a1 {\n    inputId = \n    type = CopyAction\n  }\n}", 4, 999) shouldBe List("actions", "a1", "type")

    val unclosed = "actions {\n  a1 {\n    type = CopyAction\n    "
    HoconSyntaxParser.parse(unclosed).errors.map(e => (e.message, e.start)) shouldBe List(("Unclosed '{'", 8), ("Unclosed '{'", 15))
    pathAt(unclosed, 4, 4) shouldBe List("actions", "a1")

    HoconSyntaxParser.parse("a = [1, 2,]\nb { c = 1, }").errors shouldBe empty

    val key = "actions {\n  a1 {\n    inp\n    type = CopyAction\n  }\n}"
    HoconSyntaxParser.parse(key).errors.map(_.message) shouldBe List("Expected '=', ':' or '{' after the key")
    pathAt(key, 3, 7) shouldBe List("actions", "a1")
    pathAt(key, 4, 999) shouldBe List("actions", "a1", "type")
  }

  it should "recover from misplaced characters" in {
    val document = HoconSyntaxParser.parse("a {\n  b = 1 = 2\n  ]\n  c = [1 }\n}\nd = 3")
    // The brace meant to close the array closes the object instead, so the last brace is unexpected
    document.errors.map(_.message) shouldBe
      List("Expected a new line or ',' after the field", "Unexpected ']', expected a key", "Unclosed '['", "Unexpected '}', expected a key")
    document.root.fields.map(_.keyNames) shouldBe List(List("a"), List("d"))
  }

  it should "report unterminated strings" in {
    HoconSyntaxParser.parse("a = \"abc\nb = 1").errors.map(_.message) shouldBe List("Unterminated string")
    HoconSyntaxParser.parse("a = \"\"\"abc").errors.map(_.message) shouldBe List("Unterminated multi-line string")
  }

  it should "parse includes and substitutions" in {
    val document = HoconSyntaxParser.parse("include \"base.conf\"\ninclude required(file(\"x.conf\"))\na = ${base} { b = ${?c} }")
    document.errors shouldBe empty
    document.root.includes.map(_.target) shouldBe List("\"base.conf\"", "required(file(\"x.conf\"))")
    document.root.fields.map(_.keyNames) shouldBe List(List("a"))
  }

  it should "find the occurrences of a path" in {
    val document = HoconSyntaxParser.parse("dataObjects {\n  a { type = X }\n}\ndataObjects.b.type = Y\ndataObjects { \"a\" { path = p } }")
    document.find(List("dataObjects", "a")).map(m => document.positionAt(m.segment.start)) shouldBe List((1, 2), (4, 15))
    document.find(List("dataObjects", "b", "type")).flatMap(_.value).flatMap(_.stringValue) shouldBe List("Y")
    document.find(List("dataObjects")).flatMap(document.children).map(_.segment.name) shouldBe List("a", "b", "a")
  }

  it should "convert lines and offsets" in {
    val text = loadFile("fixture/hocon/with-lists-example.conf")
    val document = HoconSyntaxParser.parse(text)
    document.offsetAt(0, 4) shouldBe 4
    document.offsetAt(0, 999) shouldBe 9
    document.offsetAt(2, 4) shouldBe 38
    document.offsetAt(2, 999) shouldBe 55
    document.offsetAt(16, 16) shouldBe 376
    document.positionAt(376) shouldBe (16, 16)
    HoconSyntaxParser.parse("a\r\nb").offsetAt(0, 999) shouldBe 1
  }

  it should "rebuild the text from the tokens" in {
    val text = loadFile("fixture/hocon/airport-example.conf")
    HoconSyntaxParser.parse(text).tokens.map(_.text).mkString shouldBe text
  }

  private def pathAt(text: String, line: Int, col: Int): List[String] =
    val document = HoconSyntaxParser.parse(text)
    document.parentPathAt(document.offsetAt(line - 1, col))

  private def wordAt(text: String, line: Int, col: Int): String =
    val document = HoconSyntaxParser.parse(text)
    document.wordAt(document.offsetAt(line - 1, col))

  private def validateText(text: String, column: Int, caretDataList: List[CaretData]): Unit =
    val totalLines = text.count(_ == '\n') + 1
    for i <- 1 to totalLines do
      CaretData(i, column, pathAt(text, i, column)) should be(caretDataList(i - 1))


}
