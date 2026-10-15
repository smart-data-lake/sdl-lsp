package io.smartdatalake.navigation

import com.typesafe.config.{Config, ConfigObject}
import io.smartdatalake.context.{References, SDLBContext}
import io.smartdatalake.context.hocon.{HoconDocument, PathMatch}
import io.smartdatalake.context.References.{ACTIONS, CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{DocumentSymbol, Location, Position, Range, SymbolKind}

import scala.util.Try

/** A value referencing the object with the given id of the given section */
case class Reference(section: String, id: String, location: Location)

/**
  * A config file as seen by the navigation features: its current text and its last valid unresolved config.
  * @param parseSyntax gives the syntax tree of the text, e.g. the one already parsed by the context of the file
  */
case class ConfigFile(uri: String, text: String, config: Config)(parseSyntax: () => HoconDocument = () => HoconDocument.parse(text)):
  /** Syntax tree of the current text, giving the locations */
  lazy val syntax: HoconDocument = parseSyntax()

  def range(start: Int, end: Int): Range =
    val (startLine, startColumn) = syntax.positionAt(start)
    val (endLine, endColumn) = syntax.positionAt(end)
    Range(Position(startLine, startColumn), Position(endLine, endColumn))

  def location(start: Int, end: Int): Location = Location(uri, range(start, end))

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
    file.syntax.find(List(section, id)).headOption.map(m => file.location(m.segment.start, m.segment.end))

  private def referencesIn(file: ConfigFile, section: String, id: String): List[Location] =
    allReferences(file).collect { case Reference(`section`, `id`, location) => location }

  /** All values of the file referencing another object by id, e.g. inputIds of actions. Substitutions are not followed */
  def allReferences(file: ConfigFile): List[Reference] =
    for
      field <- file.syntax.allFields
      section <- field.key.lastOption.flatMap(segment => References.referencedSection(segment.name)).toList
      value <- field.value.toList
      element <- value.array.map(_.elements).getOrElse(List(value))
      id <- element.stringValue.toList
    yield Reference(section, id, file.location(element.contentStart, element.contentEnd))

  /** Ids of the objects defined in the given section of the files */
  def definedIds(files: Iterable[ConfigFile], section: String): Set[String] =
    files.flatMap(file => Try(file.config.root().get(section)).toOption.collect { case obj: ConfigObject => obj.keySet.toScala }.getOrElse(Set.empty)).toSet

  /** Outline of a file: dataObjects, actions and connections, in the order of the file */
  def documentSymbols(file: ConfigFile): List[DocumentSymbol] =
    List(DATA_OBJECTS -> SymbolKind.Struct, ACTIONS -> SymbolKind.Function, CONNECTIONS -> SymbolKind.Interface)
      .flatMap((section, kind) => file.syntax.find(List(section)).map { sectionMatch =>
        val children = file.syntax.children(sectionMatch).map(child => symbol(file, child, kind, detail = objectType(file, child)))
        val sectionSymbol = symbol(file, sectionMatch, SymbolKind.Namespace, detail = "")
        sectionSymbol.setChildren(children.toJava)
        sectionSymbol
      })
      .sortBy(symbol => (symbol.getRange.getStart.getLine, symbol.getRange.getStart.getCharacter))

  /** Symbol of the given occurrence of a path, from its key to the end of its value */
  private def symbol(file: ConfigFile, m: PathMatch, kind: SymbolKind, detail: String): DocumentSymbol =
    val segment = m.segment
    // Include the quotes of a quoted key
    val start = if segment.start > 0 && file.text(segment.start - 1) == '"' then segment.start - 1 else segment.start
    DocumentSymbol(segment.name, kind, file.range(start, math.max(m.field.end, segment.end)), file.range(segment.start, segment.end), detail)

  private def objectType(file: ConfigFile, m: PathMatch): String =
    file.syntax.children(m).filter(child => child.segment.name == "type" && child.isWholeKey)
      .flatMap(_.value.flatMap(_.stringValue)).lastOption.getOrElse("")
