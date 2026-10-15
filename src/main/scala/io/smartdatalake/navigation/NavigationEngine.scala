package io.smartdatalake.navigation

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue, ConfigValueType}
import io.smartdatalake.context.{References, SDLBContext}
import io.smartdatalake.context.hocon.HoconScanner
import io.smartdatalake.context.References.{ACTIONS, CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{DocumentSymbol, Location, Position, Range, SymbolKind}

import scala.util.Try

/** A value referencing the object with the given id of the given section */
case class Reference(section: String, id: String, location: Location)

/** A config file as seen by the navigation features: its current text and its last valid unresolved config */
case class ConfigFile(uri: String, text: String, config: Config):
  lazy val lines: Array[String] = text.split("\\R", -1)

/** Go to definition, find references and outline of dataObjects, actions and connections */
class NavigationEngine:

  /** Where the object referenced (or defined) under the caret is defined */
  def definitions(context: SDLBContext, files: Iterable[ConfigFile]): List[Location] =
    target(context).toList.flatMap((section, id) => files.flatMap(definition(_, section, id)))

  /** Where the object referenced (or defined) under the caret is referenced */
  def references(context: SDLBContext, files: Iterable[ConfigFile], includeDeclaration: Boolean): List[Location] =
    target(context).toList.flatMap { (section, id) =>
      val declarations = if includeDeclaration then files.flatMap(definition(_, section, id)) else Nil
      declarations ++ files.flatMap(file => referencesIn(file, section, id))
    }

  /** Section and id of the object under the caret: either its definition key or a reference to it */
  private[navigation] def target(context: SDLBContext): Option[(String, String)] =
    val id = References.cleanId(context.word)
    val section = context.parentPath match
      case List(section) if section == DATA_OBJECTS || section == CONNECTIONS => Some(section)
      case path => path.lastOption.flatMap(References.referencedSection)
    section.filter(_ => id.nonEmpty).map(_ -> id)

  private def definition(file: ConfigFile, section: String, id: String): Option[Location] =
    Try(file.config.root().get(section)).toOption.flatMap {
      case sectionObject: ConfigObject => Option(sectionObject.get(id)).flatMap(location(file, _, id))
      case _ => None
    }

  private def referencesIn(file: ConfigFile, section: String, id: String): List[Location] =
    allReferences(file).collect { case Reference(`section`, `id`, location) => location }

  /** All values of the file referencing another object by id, e.g. inputIds of actions */
  def allReferences(file: ConfigFile): List[Reference] =
    def stringValue(value: ConfigValue): Option[String] =
      Try(Option.when(value.valueType == ConfigValueType.STRING)(value.unwrapped.toString)).toOption.flatten

    def reference(section: String, value: ConfigValue): Option[Reference] =
      stringValue(value).flatMap(id => location(file, value, id).map(Reference(section, id, _)))

    def search(value: ConfigValue): List[Reference] = Try(value match
      case obj: ConfigObject => obj.toScala.toList.flatMap { (key, child) =>
        References.referencedSection(key) match
          case Some(section) => child match
            case list: ConfigList => list.toScala.flatMap(reference(section, _))
            case single => reference(section, single).toList
          case None => search(child)
      }
      case list: ConfigList => list.toScala.flatMap(search)
      case _ => Nil
    ).getOrElse(Nil) // unresolved values can't be inspected

    search(file.config.root())

  /** Ids of the objects defined in the given section of the files */
  def definedIds(files: Iterable[ConfigFile], section: String): Set[String] =
    files.flatMap(file => Try(file.config.root().get(section)).toOption.collect { case obj: ConfigObject => obj.keySet.toScala }.getOrElse(Set.empty)).toSet

  /** Outline of a file: dataObjects, actions and connections, in the order of the file */
  def documentSymbols(file: ConfigFile): List[DocumentSymbol] =
    List(DATA_OBJECTS -> SymbolKind.Struct, ACTIONS -> SymbolKind.Function, CONNECTIONS -> SymbolKind.Interface)
      .flatMap((section, kind) => sectionSymbol(file, section, kind))
      .sortBy(_.getRange.getStart.getLine)

  private def sectionSymbol(file: ConfigFile, section: String, kind: SymbolKind): Option[DocumentSymbol] =
    Try(file.config.root().get(section)).toOption.collect { case sectionObject: ConfigObject => sectionObject }.flatMap { sectionObject =>
      val children = sectionObject.toScala.toList.flatMap { (id, value) =>
        symbol(file, id, value, kind, detail = objectType(value))
      }.sortBy(_.getRange.getStart.getLine)
      symbol(file, section, sectionObject, SymbolKind.Namespace, detail = "").map { sectionSymbol =>
        sectionSymbol.setChildren(children.toJava)
        sectionSymbol
      }
    }

  /** Symbol of the given value, from its key to the end of its block */
  private def symbol(file: ConfigFile, name: String, value: ConfigValue, kind: SymbolKind, detail: String): Option[DocumentSymbol] =
    location(file, value, name).map { keyLocation =>
      val start = keyLocation.getRange.getStart
      val afterKey = HoconScanner.lineStart(file.text, start.getLine) + start.getCharacter + name.length
      // Skip the closing quote of a quoted key
      val keyOffset = if afterKey < file.text.length && file.text(afterKey) == '"' then afterKey + 1 else afterKey
      val end = value match
        case _: ConfigObject => HoconScanner.blockEnd(file.text, keyOffset)
          .map(offset => Position(HoconScanner.lineOf(file.text, offset), offset - HoconScanner.lineStart(file.text, HoconScanner.lineOf(file.text, offset)) + 1))
        case _ => None
      val range = Range(start, end.getOrElse(Position(start.getLine, file.lines(start.getLine).length)))
      DocumentSymbol(name, kind, range, keyLocation.getRange, detail)
    }

  private def objectType(value: ConfigValue): String = value match
    case obj: ConfigObject => Option(obj.get("type")).flatMap(v => Try(v.unwrapped.toString).toOption).getOrElse("")
    case _ => ""

  /** Location of the id on the line where the value is defined */
  private def location(file: ConfigFile, value: ConfigValue, id: String): Option[Location] =
    val line = value.origin.lineNumber - 1
    Option.when(line >= 0 && line < file.lines.length) {
      val column = math.max(file.lines(line).indexOf(id), 0)
      Location(file.uri, Range(Position(line, column), Position(line, column + id.length)))
    }
