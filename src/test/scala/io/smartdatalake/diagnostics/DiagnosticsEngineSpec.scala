package io.smartdatalake.diagnostics

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.navigation.{ConfigFile, NavigationEngine}
import org.eclipse.lsp4j.DiagnosticSeverity

class DiagnosticsEngineSpec extends UnitSpec:

    private val engine = DiagnosticsEngine(schemaReader, NavigationEngine())
    private val uri = "file:///ws/actions.conf"
    private val objectsUri = "file:///ws/objects.conf"
    private val objectsText = "dataObjects {\n  ext-departures { type = CsvFileDataObject }\n}\nconnections {\n  my-db { type = JdbcTableConnection }\n}"
    private val objectsFile = ConfigFile(objectsUri, objectsText, SDLBContext.parse(objectsUri, objectsText).get)

    /** (severity, 1-based line, column, message) of each diagnostic */
    private def check(text: String, files: List[ConfigFile] = List(objectsFile)) =
        engine.diagnostics(uri, text, files).map(d => (d.getSeverity, d.getRange.getStart.getLine + 1, d.getRange.getStart.getCharacter, d.getMessage))

    "Diagnostics engine" should "report syntax errors with their line" in {
        val diagnostics = check("actions {\n  a1 {\n    type = CopyAction\n  \n}")
        diagnostics should have size 1
        val (severity, line, _, message) = diagnostics.head
        severity shouldBe DiagnosticSeverity.Error
        line shouldBe 5
        message should not include (uri)
    }

    it should "report references to unknown dataObjects and connections" in {
        val text = "actions {\n  a1 {\n    type = CopyAction\n    inputId = ext-departures\n    outputIds = [ext-departures, unknown-obj]\n  }\n}\n" +
            "dataObjects {\n  obj { type = JdbcTableDataObject, connectionId = unknown-db }\n}"
        check(text) shouldBe List(
            (DiagnosticSeverity.Warning, 5, 33, "Unknown dataObject 'unknown-obj'"),
            (DiagnosticSeverity.Warning, 9, 51, "Unknown connection 'unknown-db'")
        )
    }

    it should "accept references to objects defined in the file itself" in {
        check("dataObjects {\n  a { type = CsvFileDataObject }\n}\nactions {\n  a1 { type = CopyAction, inputId = a, outputId = a }\n}", files = Nil) shouldBe empty
    }

    it should "report unknown built-in types but not custom classes" in {
        val text = "actions {\n  a1 { type = CopyActon, inputId = ext-departures, outputId = ext-departures }\n" +
            "  a2 { type = com.mycompany.MyAction, inputId = ext-departures, outputId = ext-departures }\n}"
        check(text) shouldBe List(
            (DiagnosticSeverity.Warning, 2, 14, "Unknown type 'CopyActon' for actions. Custom types must be fully qualified class names")
        )
    }

    it should "ignore unresolved substitutions" in {
        check("actions {\n  a1 { type = CopyAction, inputId = ${myInput}, outputId = ext-departures }\n}") shouldBe empty
    }
