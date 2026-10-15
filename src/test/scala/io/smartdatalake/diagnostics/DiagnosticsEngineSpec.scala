package io.smartdatalake.diagnostics

import io.smartdatalake.UnitSpec
import io.smartdatalake.navigation.{ConfigFile, NavigationEngine}
import org.eclipse.lsp4j.DiagnosticSeverity

class DiagnosticsEngineSpec extends UnitSpec:

    private val engine = DiagnosticsEngine(schemaReader, NavigationEngine())
    private val uri = "file:///ws/actions.conf"
    private val objectsUri = "file:///ws/objects.conf"
    private val objectsText = "dataObjects {\n  ext-departures { type = CsvFileDataObject }\n}\nconnections {\n  my-db { type = JdbcTableConnection }\n}"
    private val objectsFile = ConfigFile(objectsUri, objectsText)

    /** (severity, 1-based line, column, message) of each diagnostic */
    private def check(text: String, files: List[ConfigFile] = List(objectsFile)) =
        engine.diagnostics(ConfigFile(uri, text), files).map(d => (d.getSeverity, d.getRange.getStart.getLine + 1, d.getRange.getStart.getCharacter, d.getMessage))

    "Diagnostics engine" should "report syntax errors with their position" in {
        // The last brace closes a1, so actions is not closed
        check("actions {\n  a1 {\n    type = CopyAction\n  \n}") shouldBe List((DiagnosticSeverity.Error, 1, 8, "Unclosed '{'"))
    }

    it should "report all syntax errors" in {
        check("actions {\n  a1 {\n    inputId = \n    type = [CopyAction\n  }\n}") shouldBe List(
            (DiagnosticSeverity.Error, 3, 12, "Missing value"),
            (DiagnosticSeverity.Error, 4, 11, "Unclosed '['")
        )
    }

    it should "report the error of Typesafe Config if the syntax tree has none" in {
        val diagnostics = check("a = ${b} { c = 1 }\nb = 1\nd = [1] { e = 2 }")
        diagnostics should have size 1
        val (severity, _, _, message) = diagnostics.head
        severity shouldBe DiagnosticSeverity.Error
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
