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
        engine.diagnostics(ConfigFile(uri, text), files).map(d => (d.getSeverity, d.getRange.getStart.getLine + 1, d.getRange.getStart.getCharacter, d.getMessage.getLeft))

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
        val text = "actions {\n  a1 {\n    type = CustomDataFrameAction\n    inputIds = [ext-departures]\n    outputIds = [ext-departures, unknown-obj]\n  }\n}\n" +
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

    it should "report substitutions of unknown paths, but not check references given by substitutions" in {
        check("actions {\n  a1 { type = CopyAction, inputId = ${myInput}, outputId = ext-departures }\n}") shouldBe List(
            (DiagnosticSeverity.Warning, 2, 36, "Unknown substitution 'myInput'"))
    }

    it should "accept substitutions defined in the file, in other files, through other substitutions, as environment variable or system property" in {
        val templatesFile = ConfigFile("file:///ws/templates.conf", "templates {\n  slvTable { type = DeltaLakeTableDataObject, table { db = slv } }\n  \"with.dot\" = 1\n}")
        val text = "dataObjects {\n  obj = ${templates.slvTable} { table.name = ${local.name} }\n" +
            "  obj2 = ${dataObjects.obj} { table.db = ${dataObjects.obj.table.db}, table.name = ${ templates.\"with.dot\" } }\n}\n" +
            "local.name = n\nhome = ${HOME}\nuserHome = ${user.home}\noptional = ${?not.defined}"
        check(text, files = List(objectsFile, templatesFile)) shouldBe empty
    }

    it should "report each unknown path once, not the substitutions depending on it" in {
        val text = "a = ${missing} { b = 1 }\nc = ${a.b}\nd = 1\ne = ${d.f}\ng = ${templates.unknown}"
        check(text) shouldBe List(
            (DiagnosticSeverity.Warning, 1, 4, "Unknown substitution 'missing'"),
            (DiagnosticSeverity.Warning, 4, 4, "Unknown substitution 'd.f'"),
            (DiagnosticSeverity.Warning, 5, 4, "Unknown substitution 'templates.unknown'")
        )
    }

    it should "report attributes unknown to the schema of their object" in {
        val text = "actions {\n  a1 {\n    type = CopyAction\n    inputId = ext-departures\n    outputId = ext-departures\n    abc = 1\n" +
            "    metadata { feed = f, unknownMeta = x }\n  }\n}"
        check(text) shouldBe List(
            (DiagnosticSeverity.Warning, 6, 4, "Unknown attribute 'abc' of CopyAction"),
            (DiagnosticSeverity.Warning, 7, 25, "Unknown attribute 'unknownMeta' of ActionMetadata")
        )
    }

    it should "know the type of an object given by a substitution from another file, and check each segment of dotted keys" in {
        val templatesFile = ConfigFile("file:///ws/templates.conf", "templates {\n  slvTable { type = DeltaLakeTableDataObject, table { db = slv } }\n}")
        val text = "dataObjects {\n  obj = ${templates.slvTable} {\n    table {\n      name = t\n      abc = test\n    }\n    table.def = 1\n    tabel.name = t\n  }\n}"
        check(text, files = List(objectsFile, templatesFile)) shouldBe List(
            (DiagnosticSeverity.Warning, 5, 6, "Unknown attribute 'abc' of Table"),
            (DiagnosticSeverity.Warning, 7, 10, "Unknown attribute 'def' of Table"),
            (DiagnosticSeverity.Warning, 8, 4, "Unknown attribute 'tabel' of DeltaLakeTableDataObject")
        )
    }

    it should "not check objects of custom or unknown types, free maps and objects outside of the schema" in {
        val text = "dataObjects {\n  custom { type = com.mycompany.MyDataObject, abc = 1 }\n  unknown { type = UnknownDataObject, abc = 1 }\n" +
            "  delta { type = DeltaLakeTableDataObject, table { db = d, name = n }, options { anyOption = x } }\n}\n" +
            "templates {\n  t { abc = 1 }\n}\nmyVariable = 1"
        check(text).map(_._4) shouldBe List("Unknown type 'UnknownDataObject' for dataObjects. Custom types must be fully qualified class names")
    }

    it should "accept attributes in kebab-case" in {
        check("actions {\n  a1 { type = CopyAction, input-id = ext-departures, output-id = ext-departures, delete-data-after-read = true }\n}") shouldBe empty
    }
