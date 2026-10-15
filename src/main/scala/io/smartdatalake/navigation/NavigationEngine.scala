package io.smartdatalake.navigation

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue, ConfigValueType}
import io.smartdatalake.context.{References, SDLBContext}
import io.smartdatalake.context.References.{CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{Location, Position, Range}

import scala.util.Try

/** A config file as seen by the navigation features: its current text and its last valid unresolved config */
case class ConfigFile(uri: String, text: String, config: Config):
  lazy val lines: Array[String] = text.split("\\R", -1)

/** Go to definition and find references of dataObjects and connections, across the given files */
class NavigationEngine:

  /** Where the object referenced (or defined) under the caret is defined */
  def definitions(context: SDLBContext, files: Iterable[ConfigFile]): List[Location] =
    target(context).toList.flatMap((section, id) => files.flatMap(definition(_, section, id)))

  /** Where the object referenced (or defined) under the caret is referenced */
  def references(context: SDLBContext, files: Iterable[ConfigFile], includeDeclaration: Boolean): List[Location] =
    target(context).toList.flatMap { (section, id) =>
      val declarations = if includeDeclaration then files.flatMap(definition(_, section, id)) else Nil
      declarations ++ files.flatMap(file => referencesIn(file, References.attributesReferencing(section), id))
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

  private def referencesIn(file: ConfigFile, attributes: Set[String], id: String): List[Location] =
    def isId(value: ConfigValue): Boolean =
      Try(value.valueType == ConfigValueType.STRING && value.unwrapped == id).getOrElse(false)

    def search(value: ConfigValue): List[Location] = Try(value match
      case obj: ConfigObject => obj.toScala.toList.flatMap { (key, child) =>
        if attributes.contains(key) then child match
          case list: ConfigList => list.toScala.filter(isId).flatMap(location(file, _, id))
          case single if isId(single) => location(file, single, id).toList
          case _ => Nil
        else search(child)
      }
      case list: ConfigList => list.toScala.flatMap(search)
      case _ => Nil
    ).getOrElse(Nil) // unresolved values can't be inspected

    search(file.config.root())

  /** Location of the id on the line where the value is defined */
  private def location(file: ConfigFile, value: ConfigValue, id: String): Option[Location] =
    val line = value.origin.lineNumber - 1
    Option.when(line >= 0 && line < file.lines.length) {
      val column = math.max(file.lines(line).indexOf(id), 0)
      Location(file.uri, Range(Position(line, column), Position(line, column + id.length)))
    }
