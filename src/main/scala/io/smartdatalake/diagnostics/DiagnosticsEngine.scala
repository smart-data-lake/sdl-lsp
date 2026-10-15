package io.smartdatalake.diagnostics

import com.typesafe.config.{Config, ConfigException, ConfigObject, ConfigUtil, ConfigValue, ConfigValueType}
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.context.References.{ACTIONS, CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.navigation.{ConfigFile, NavigationEngine, Reference}
import io.smartdatalake.schema.SchemaReaderImpl
import org.eclipse.lsp4j.{Diagnostic, DiagnosticSeverity, Position, Range}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * Problems of a config file:
  * - HOCON syntax errors, only these while the file doesn't parse
  * - references to dataObjects or connections defined in none of the visible files
  * - unknown built-in types (custom types are fully qualified class names, so they are not checked)
  * - attributes unknown to the schema of their object, if the schema allows no other attributes
  * - substitutions of paths defined in none of the visible files
  */
class DiagnosticsEngine(schemaReader: SchemaReaderImpl, navigationEngine: NavigationEngine):
  import DiagnosticsEngine.*

  /** @param visibleFiles files seen from the document, used to know which objects are defined */
  def diagnostics(file: ConfigFile, visibleFiles: Iterable[ConfigFile]): List[Diagnostic] =
    file.source.configOrError match
      case Left(error) =>
        // Typesafe Config only reports the first error, and may find errors the syntax tree doesn't know about
        if file.syntax.errors.isEmpty then List(syntaxError(file.text, error))
        else file.syntax.errors.map(e => diagnostic(file.range(e.start, math.max(e.end, e.start + 1)), DiagnosticSeverity.Error, e.message))
      case Right(_) =>
        // The config of a valid file is the config of its current text
        val files = file :: visibleFiles.filterNot(_.uri == file.uri).toList
        // The config of the file resolved with the other files, as seen by SDLB
        val othersConfig = files.tail.map(_.config).foldLeft(SDLBContext.EMPTY_CONFIG)(_.withFallback(_))
        val rootConfig = SDLBContext.fromSource(file.source, othersConfig).rootConfig
        (unknownReferences(file, files) ++ unknownTypes(file) ++ unknownAttributes(file, rootConfig) ++ unknownSubstitutions(file, rootConfig)).sortBy(d => (d.getRange.getStart.getLine, d.getRange.getStart.getCharacter))

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

  /**
    * Keys of the file unknown to the schema of their object. The type of an object is read from the config resolved with the other files,
    * so that types given by a substitution, e.g. `obj = ${templates.t1} {...}`, are known. Each segment of a dotted key is checked.
    */
  private def unknownAttributes(file: ConfigFile, resolvedConfig: Config): List[Diagnostic] =
    val rootConfig = resolvedConfig.root()
    val allowedAttributes = mutable.Map.empty[List[String], Option[(String, Set[String])]]
    for
      (field, objectPath) <- file.syntax.fieldPaths
      (segment, index) <- field.key.zipWithIndex
      path = objectPath ++ field.key.take(index).map(_.name)
      (schemaName, attributes) <- allowedAttributes.getOrElseUpdate(path, schemaReader.allowedAttributes(path, rootConfig)).toList
      // SDLB also accepts kebab-case names, e.g. csv-options for csvOptions
      if !attributes.contains(segment.name) && !attributes.contains(camelCase(segment.name))
    yield
      val of = if schemaName.isEmpty then path.lastOption.getOrElse("") else schemaName
      diagnostic(file.range(segment.start, segment.end), DiagnosticSeverity.Warning, s"Unknown attribute '${segment.name}' of $of")

  /**
    * Substitutions of paths defined neither in the config resolved with the other files, nor as environment variable or Java system property (e.g. user.home).
    * Optional substitutions are not checked, nor paths depending on a value which is not resolved itself: only the root cause is reported.
    */
  private def unknownSubstitutions(file: ConfigFile, resolvedConfig: Config): List[Diagnostic] =
    file.syntax.substitutions.collect {
      case (token, segments) if !token.text.startsWith("${?") && segments.nonEmpty && token.text.endsWith("}") =>
        val path = ConfigUtil.joinPath(segments.map(_.name).toJava)
        val defined = isDefined(resolvedConfig.root(), segments.map(_.name)) || sys.env.contains(path) || sys.props.contains(path)
        Option.when(!defined)(diagnostic(file.range(token.start, token.end), DiagnosticSeverity.Warning, s"Unknown substitution '$path'"))
    }.flatten

  /** Whether the path is defined under the value, or may be: values which are not resolved have an unknown content */
  @tailrec
  private def isDefined(value: ConfigValue, path: List[String]): Boolean = path match
    case Nil => true
    case key :: rest => value match
      case obj: ConfigObject => Try(Option(obj.get(key))) match
        case Failure(_) => true // an object merged with an unresolved substitution
        case Success(None) => false
        case Success(Some(child)) => isDefined(child, rest)
      case other => Try(other.valueType).isFailure // a value of an unresolved substitution can be an object

  private def objectsOf(file: ConfigFile, section: String): List[(String, ConfigObject)] =
    Try(file.config.root().get(section)).toOption.collect { case sectionObject: ConfigObject =>
      sectionObject.toScala.toList.collect { case (id, obj: ConfigObject) => id -> obj }
    }.getOrElse(Nil)

object DiagnosticsEngine:
  val SOURCE = "sdl-lsp"

  private def camelCase(name: String): String =
    "-([a-z0-9])".r.replaceAllIn(name, m => m.group(1).toUpperCase)

  private def singular(section: String): String = section match
    case DATA_OBJECTS => "dataObject"
    case CONNECTIONS => "connection"
    case other => other

  private def diagnostic(range: Range, severity: DiagnosticSeverity, message: String): Diagnostic =
    Diagnostic(range, message, severity, SOURCE)
