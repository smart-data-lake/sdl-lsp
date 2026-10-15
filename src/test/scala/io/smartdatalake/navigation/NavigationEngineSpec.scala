package io.smartdatalake.navigation

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.Location

class NavigationEngineSpec extends UnitSpec:

    private val objectsUri = "file:///ws/objects.conf"
    private val objectsText =
        """connections {
          |  my-db { type = JdbcTableConnection }
          |}
          |dataObjects {
          |  ext-departures {
          |    type = CsvFileDataObject
          |  }
          |  "quoted-obj" {
          |    type = JdbcTableDataObject
          |    connectionId = my-db
          |  }
          |}""".stripMargin
    private val actionsUri = "file:///ws/actions.conf"
    private val actionsText =
        """actions {
          |  a1 {
          |    type = CopyAction
          |    inputId = ext-departures
          |    outputId = "quoted-obj"
          |  }
          |  a2 {
          |    type = CustomDataFrameAction
          |    inputIds = [ext-departures, quoted-obj]
          |    outputIds = [
          |      "ext-departures"
          |    ]
          |  }
          |}""".stripMargin

    private def file(uri: String, text: String) = ConfigFile(uri, text)
    private val files = List(file(objectsUri, objectsText), file(actionsUri, actionsText))
    private val engine = NavigationEngine()

    /** (uri, 1-based line, column) of each location */
    private def positions(locations: List[Location]): Set[(String, Int, Int)] =
        locations.map(l => (l.getUri, l.getRange.getStart.getLine + 1, l.getRange.getStart.getCharacter)).toSet

    private def actionsContext(line: Int, column: Int) = SDLBContext.fromText(actionsUri, actionsText).withCaretPosition(line, column)
    private def objectsContext(line: Int, column: Int) = SDLBContext.fromText(objectsUri, objectsText).withCaretPosition(line, column)

    "Navigation engine" should "find the definition of a dataObject referenced in another file" in {
        positions(engine.definitions(actionsContext(4, 18), files)) shouldBe Set((objectsUri, 5, 2))
        positions(engine.definitions(actionsContext(5, 18), files)) shouldBe Set((objectsUri, 8, 3))
    }

    it should "find the definition from ids in lists" in {
        positions(engine.definitions(actionsContext(9, 20), files)) shouldBe Set((objectsUri, 5, 2))
        positions(engine.definitions(actionsContext(9, 37), files)) shouldBe Set((objectsUri, 8, 3))
        positions(engine.definitions(actionsContext(11, 9), files)) shouldBe Set((objectsUri, 5, 2))
    }

    it should "find the definition of a connection" in {
        positions(engine.definitions(objectsContext(10, 21), files)) shouldBe Set((objectsUri, 2, 2))
    }

    it should "find all references of a dataObject from its definition" in {
        positions(engine.references(objectsContext(5, 5), files, includeDeclaration = false)) shouldBe
            Set((actionsUri, 4, 14), (actionsUri, 9, 16), (actionsUri, 11, 7))
        positions(engine.references(objectsContext(5, 5), files, includeDeclaration = true)) should contain ((objectsUri, 5, 2))
    }

    it should "find all references of a dataObject from a reference" in {
        positions(engine.references(actionsContext(5, 18), files, includeDeclaration = false)) shouldBe
            Set((actionsUri, 5, 16), (actionsUri, 9, 32))
    }

    it should "find nothing outside of references" in {
        engine.definitions(actionsContext(3, 13), files) shouldBe empty
        engine.references(actionsContext(2, 3), files, includeDeclaration = true) shouldBe empty
    }

    private val templatesUri = "file:///ws/templates.conf"
    private val templatesText =
        """templates {
          |  slvMarsTable {
          |    type = DeltaLakeTableDataObject
          |    table { db = slv }
          |  }
          |  "with.dot" = { type = CsvFileDataObject }
          |}""".stripMargin
    private val usagesUri = "file:///ws/usages.conf"
    private val usagesText =
        """dataObjects {
          |  slv-ana-positionspunkt = ${templates.slvMarsTable} {
          |    table.name = positionspunkt
          |  }
          |  slv-other = ${ templates.slvMarsTable } { table.db = ${templates.slvMarsTable.table.db} }
          |  csv = ${?templates."with.dot"}
          |  unknown = ${templates.missing}
          |}""".stripMargin
    private val substitutionFiles = List(file(templatesUri, templatesText), file(usagesUri, usagesText))

    private def usagesContext(line: Int, column: Int) = SDLBContext.fromText(usagesUri, usagesText).withCaretPosition(line, column)
    private def templatesContext(line: Int, column: Int) = SDLBContext.fromText(templatesUri, templatesText).withCaretPosition(line, column)

    it should "find the definition of the path of a substitution" in {
        // On the last segment, on the braces and in a substitution with spaces: the whole path
        positions(engine.definitions(usagesContext(2, 40), substitutionFiles)) shouldBe Set((templatesUri, 2, 2))
        positions(engine.definitions(usagesContext(2, 27), substitutionFiles)) shouldBe Set((templatesUri, 2, 2))
        positions(engine.definitions(usagesContext(5, 30), substitutionFiles)) shouldBe Set((templatesUri, 2, 2))
        // On a segment before the last one: the path up to this segment
        positions(engine.definitions(usagesContext(2, 30), substitutionFiles)) shouldBe Set((templatesUri, 1, 0))
        positions(engine.definitions(usagesContext(5, 82), substitutionFiles)) shouldBe Set((templatesUri, 4, 4))
        // Optional substitution with a quoted segment
        positions(engine.definitions(usagesContext(6, 26), substitutionFiles)) shouldBe Set((templatesUri, 6, 3))
    }

    it should "find nothing for a substitution of an unknown path" in {
        engine.definitions(usagesContext(7, 25), substitutionFiles) shouldBe empty
    }

    it should "find the substitutions of a path from its definition and from a substitution" in {
        val usages = Set((usagesUri, 2, 39), (usagesUri, 5, 27), (usagesUri, 5, 67))
        positions(engine.references(templatesContext(2, 5), substitutionFiles, includeDeclaration = false)) shouldBe usages
        positions(engine.references(usagesContext(2, 40), substitutionFiles, includeDeclaration = false)) shouldBe usages
        positions(engine.references(usagesContext(2, 40), substitutionFiles, includeDeclaration = true)) shouldBe usages + ((templatesUri, 2, 2))
        positions(engine.references(templatesContext(4, 13), substitutionFiles, includeDeclaration = false)) shouldBe Set((usagesUri, 5, 86))
    }

    it should "provide the outline of a file" in {
        val symbols = engine.documentSymbols(file(objectsUri, objectsText))
        symbols.map(_.getName) shouldBe List("connections", "dataObjects")
        val dataObjects = symbols(1)
        (dataObjects.getRange.getStart.getLine + 1, dataObjects.getRange.getEnd.getLine + 1) shouldBe (4, 12)
        val children = dataObjects.getChildren.toScala
        children.map(c => (c.getName, c.getDetail, c.getRange.getStart.getLine + 1, c.getRange.getEnd.getLine + 1)) shouldBe
            List(("ext-departures", "CsvFileDataObject", 5, 7), ("quoted-obj", "JdbcTableDataObject", 8, 11))
        symbols.head.getChildren.toScala.map(c => (c.getName, c.getRange.getStart.getLine + 1, c.getRange.getEnd.getLine + 1)) shouldBe
            List(("my-db", 2, 2))
        engine.documentSymbols(file(actionsUri, actionsText)).head.getChildren.toScala.map(_.getName) shouldBe List("a1", "a2")
    }

