package io.smartdatalake.diagnostics

import com.typesafe.config.{ConfigException, ConfigObject, ConfigValueType}
import io.smartdatalake.context.References.{ACTIONS, CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.navigation.{ConfigFile, NavigationEngine, Reference}
import io.smartdatalake.schema.SchemaReaderImpl
import org.eclipse.lsp4j.{Diagnostic, DiagnosticSeverity, Position, Range}

import scala.util.Try

/**
  * Problems of a config file:
  * - HOCON syntax errors
  * - references to dataObjects or connections defined in none of the visible files
  * - unknown built-in types (custom types are fully qualified class names, so they are not checked)
  */
class DiagnosticsEngine(schemaReader: SchemaReaderImpl, navigationEngine: NavigationEngine):
  import DiagnosticsEngine.*

  /** @param visibleFiles files seen from the document, used to know which objects are defined */
  def diagnostics(uri: String, text: String, visibleFiles: Iterable[ConfigFile]): List[Diagnostic] =
    SDLBContext.parseOrError(uri, text) match
      case Left(error) => List(syntaxError(text, error))
      case Right(config) =>
        val file = ConfigFile(uri, text, config)
        val files = file :: visibleFiles.filterNot(_.uri == uri).toList
        (unknownReferences(file, files) ++ unknownTypes(file)).sortBy(d => (d.getRange.getStart.getLine, d.getRange.getStart.getCharacter))

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
      objectsOf(file, section).flatMap { obj =>
        Option(obj.get("type"))
          .filter(value => Try(value.valueType == ConfigValueType.STRING).getOrElse(false))
          .map(value => (value, value.unwrapped.toString))
          .filter((_, objectType) => knownTypes.nonEmpty && !objectType.contains('.') && !knownTypes.contains(objectType))
          .map { (value, objectType) =>
            val line = value.origin.lineNumber - 1
            val column = math.max(file.lines.lift(line).map(_.indexOf(objectType)).getOrElse(0), 0)
            diagnostic(Range(Position(line, column), Position(line, column + objectType.length)), DiagnosticSeverity.Warning,
              s"Unknown type '$objectType' for $section. Custom types must be fully qualified class names")
          }
      }
    }

  private def objectsOf(file: ConfigFile, section: String): List[ConfigObject] =
    Try(file.config.root().get(section)).toOption.collect { case sectionObject: ConfigObject =>
      sectionObject.toScala.values.collect { case obj: ConfigObject => obj }.toList
    }.getOrElse(Nil)

object DiagnosticsEngine:
  val SOURCE = "sdl-lsp"

  private def singular(section: String): String = section match
    case DATA_OBJECTS => "dataObject"
    case CONNECTIONS => "connection"
    case other => other

  private def diagnostic(range: Range, severity: DiagnosticSeverity, message: String): Diagnostic =
    Diagnostic(range, message, severity, SOURCE)
