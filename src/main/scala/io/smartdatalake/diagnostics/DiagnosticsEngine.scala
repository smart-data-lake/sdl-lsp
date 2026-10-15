package io.smartdatalake.diagnostics

import com.typesafe.config.{ConfigException, ConfigObject, ConfigValueType}
import io.smartdatalake.context.References.{ACTIONS, CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.context.hocon.HoconDocument
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.navigation.{ConfigFile, NavigationEngine, Reference}
import io.smartdatalake.schema.SchemaReaderImpl
import org.eclipse.lsp4j.{Diagnostic, DiagnosticSeverity, Position, Range}

import scala.util.Try

/**
  * Problems of a config file:
  * - HOCON syntax errors, only these while the file doesn't parse
  * - references to dataObjects or connections defined in none of the visible files
  * - unknown built-in types (custom types are fully qualified class names, so they are not checked)
  */
class DiagnosticsEngine(schemaReader: SchemaReaderImpl, navigationEngine: NavigationEngine):
  import DiagnosticsEngine.*

  /** @param visibleFiles files seen from the document, used to know which objects are defined */
  def diagnostics(uri: String, text: String, visibleFiles: Iterable[ConfigFile]): List[Diagnostic] =
    // The visible file with the same text already has its syntax tree
    val syntax = () => visibleFiles.find(file => file.uri == uri && file.text == text).map(_.syntax).getOrElse(HoconDocument.parse(text))
    SDLBContext.parseOrError(uri, text) match
      case Left(error) =>
        val file = ConfigFile(uri, text, SDLBContext.EMPTY_CONFIG)(syntax)
        // Typesafe Config only reports the first error, and may find errors the syntax tree doesn't know about
        if file.syntax.errors.isEmpty then List(syntaxError(text, error))
        else file.syntax.errors.map(e => diagnostic(file.range(e.start, math.max(e.end, e.start + 1)), DiagnosticSeverity.Error, e.message))
      case Right(config) =>
        val file = ConfigFile(uri, text, config)(syntax)
        val files = file :: visibleFiles.filterNot(_.uri == uri).toList
        (unknownReferences(file, files) ++ unknownTypes(file)).sortBy(d => (d.getRange.getStart.getLine, d.getRange.getStart.getCharacter))

  /** Error reported by Typesafe Config, on the whole line */
  private def syntaxError(text: String, error: ConfigException): Diagnostic =
    val lines = text.split("\\R", -1)
    val line = Option(error.origin).map(_.lineNumber - 1).filter(l => l >= 0 && l < lines.length).getOrElse(0)
    // The message starts with the origin, e.g. "file:///a.conf: 3: "
    val message = Option(error.origin).map(_.description + ": ").filter(error.getMessage.startsWith)
      .map(prefix => error.getMessage.drop(prefix.length)).getOrElse(error.getMessage)
    diagnostic(Range(Position(line, 0), Position(line, lines(line).length)), DiagnosticSeverity.Error, message)

  private def unknownReferences(file: ConfigFile, files: List[ConfigFile]): List[Diagnostic] =
    val definedIds = List(DATA_OBJECTS, CONNECTIONS).map(section => section -> navigationEngine.definedIds(files, section)).toMap
    navigationEngine.allReferences(file).collect {
      case Reference(section, id, location) if !definedIds.getOrElse(section, Set.empty).contains(id) =>
        diagnostic(location.getRange, DiagnosticSeverity.Warning, s"Unknown ${singular(section)} '$id'")
    }

  private def unknownTypes(file: ConfigFile): List[Diagnostic] =
    List(DATA_OBJECTS, ACTIONS, CONNECTIONS).flatMap { section =>
      val knownTypes = schemaReader.typeNames(section)
      objectsOf(file, section).flatMap { (id, obj) =>
        Option(obj.get("type"))
          .filter(value => Try(value.valueType == ConfigValueType.STRING).getOrElse(false))
          .map(_.unwrapped.toString)
          .filter(objectType => knownTypes.nonEmpty && !objectType.contains('.') && !knownTypes.contains(objectType))
          // The last occurrence wins, as in HOCON
          .flatMap(objectType => file.syntax.find(List(section, id, "type")).flatMap(_.value).lastOption.map { value =>
            diagnostic(file.range(value.contentStart, value.contentEnd), DiagnosticSeverity.Warning,
              s"Unknown type '$objectType' for $section. Custom types must be fully qualified class names")
          })
      }
    }

  private def objectsOf(file: ConfigFile, section: String): List[(String, ConfigObject)] =
    Try(file.config.root().get(section)).toOption.collect { case sectionObject: ConfigObject =>
      sectionObject.toScala.toList.collect { case (id, obj: ConfigObject) => id -> obj }
    }.getOrElse(Nil)

object DiagnosticsEngine:
  val SOURCE = "sdl-lsp"

  private def singular(section: String): String = section match
    case DATA_OBJECTS => "dataObject"
    case CONNECTIONS => "connection"
    case other => other

  private def diagnostic(range: Range, severity: DiagnosticSeverity, message: String): Diagnostic =
    Diagnostic(range, message, severity, SOURCE)
