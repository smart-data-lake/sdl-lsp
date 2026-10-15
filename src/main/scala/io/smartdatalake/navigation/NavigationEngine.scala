package io.smartdatalake.navigation

import com.typesafe.config.{Config, ConfigObject}
import io.smartdatalake.context.{ParsedText, References, SDLBContext}
import io.smartdatalake.context.hocon.{HoconDocument, PathMatch}
import io.smartdatalake.context.References.{ACTIONS, CONNECTIONS, DATA_OBJECTS}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{DocumentSymbol, Location, Position, Range, SymbolKind}

import scala.util.Try

/** A value referencing the object with the given id of the given section */
case class Reference(section: String, id: String, location: Location)

/**
  * A config file as seen by the navigation features and the diagnostics.
  * @param source current text of the file
  * @param config last valid unresolved config of the file
  */
case class ConfigFile(source: ParsedText, config: Config):
  def uri: String = source.uri
  def text: String = source.text

  /** Syntax tree of the current text, giving the locations */
  def syntax: HoconDocument = source.syntax

  /** The next version of the file: its config is kept while the text is invalid */
  def withText(newText: String): ConfigFile =
    val newSource = ParsedText(uri, newText)
    ConfigFile(newSource, newSource.config.getOrElse(config))

  def range(start: Int, end: Int): Range =
    val (startLine, startColumn) = syntax.positionAt(start)
    val (endLine, endColumn) = syntax.positionAt(end)
    Range(Position(startLine, startColumn), Position(endLine, endColumn))

  def location(start: Int, end: Int): Location = Location(uri, range(start, end))

/** Go to definition, find references and outline of dataObjects, actions and connections, and navigation of substitutions */
class NavigationEngine:

  /** Where the object referenced (or defined) under the caret is defined, or the path of the substitution under the caret */
  def definitions(context: SDLBContext, files: Iterable[ConfigFile]): List[Location] =
    substitutionPath(context) match
      case Some(path) => files.toList.flatMap(definitionsOfPath(_, path))
      case None => target(context).toList.flatMap((section, id) => files.flatMap(definition(_, section, id)))

  /**
   * Where the object referenced (or defined) under the caret is referenced, by id or by substitution.
   * In a substitution or on another key: the substitutions of its path.
   */
  def references(context: SDLBContext, files: Iterable[ConfigFile], includeDeclaration: Boolean): List[Location] =
    val byId = target(context).filter(_ => substitutionPath(context).isEmpty).toList.flatMap { (section, id) =>
      val declarations = if includeDeclaration then files.flatMap(definition(_, section, id)) else Nil
      declarations ++ files.flatMap(file => referencesIn(file, section, id))
    }
    val path = substitutionPath(context).orElse(context.textContext.syntax.keyPathAt(context.offset))
    val bySubstitution = path.toList.flatMap { path =>
      val substitutions = files.toList.flatMap(substitutionsOf(_, path))
      // The declarations of dataObjects and connections are already given with their references by id
      val declarations = if includeDeclaration && byId.isEmpty && substitutions.nonEmpty then files.toList.flatMap(definitionsOfPath(_, path)) else Nil
      declarations ++ substitutions
    }
    byId ++ bySubstitution

  /** Path of the substitution under the caret, up to the segment under the caret */
  private def substitutionPath(context: SDLBContext): Option[List[String]] =
    context.textContext.syntax.substitutionPathAt(context.offset).map(_.map(_.name))

  /** Occurrences of the path as a key in the file */
  private def definitionsOfPath(file: ConfigFile, path: List[String]): List[Location] =
    file.syntax.find(path).map(m => file.location(m.segment.start, m.segment.end))

  /** Substitutions of the path or of a path under it in the file, located at the segment of the last element of the path */
  private def substitutionsOf(file: ConfigFile, path: List[String]): List[Location] =
    file.syntax.substitutions.collect { case (_, segments) if segments.map(_.name).startsWith(path) =>
      val segment = segments(path.size - 1)
      file.location(segment.start, segment.end)
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

object ConfigFile:
  def apply(uri: String, text: String): ConfigFile =
    val source = ParsedText(uri, text)
    ConfigFile(source, source.config.getOrElse(SDLBContext.EMPTY_CONFIG))
