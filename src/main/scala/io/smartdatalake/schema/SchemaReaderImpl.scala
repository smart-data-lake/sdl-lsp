package io.smartdatalake.schema

import com.typesafe.config.{ConfigList, ConfigObject, ConfigValue}
import io.circe.Json
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}

import scala.annotation.tailrec
import scala.io.Source
import scala.util.{Try, Using}
import java.nio.file.{Files, Path}
import io.smartdatalake.logging.SDLBLogger

class SchemaReaderImpl(val schemaPath: String) extends SchemaReader with SDLBLogger {

  private val bundledSchema: Json = parseSchema(schemaPath, Using.resource(getClass.getClassLoader.getResourceAsStream(schemaPath)) { inputStream =>
    Source.fromInputStream(inputStream).getLines().mkString("\n").trim
  }).fold(error => throw IllegalStateException(s"Invalid schema $schemaPath", error), identity)

  @volatile private var schema: Json = bundledSchema

  /** Uses the given schema file, e.g. exported from another SDLB version, instead of the bundled schema. None to use the bundled schema */
  def useSchemaFile(file: Option[Path]): Unit =
    schema = file.flatMap { path =>
      Try(Files.readString(path)).toEither.flatMap(parseSchema(path.toString, _)) match
        case Right(json) =>
          info(s"Using schema $path")
          Some(json)
        case Left(error) =>
          warn(s"Cannot use schema $path, using the bundled schema instead: ${error.getMessage}")
          None
    }.getOrElse(bundledSchema)

  private def parseSchema(name: String, text: String): Either[Throwable, Json] =
    io.circe.parser.parse(text).left.map(error => IllegalArgumentException(s"Invalid JSON schema $name: ${error.message}"))

  private[schema] def createGlobalSchemaContext: SchemaContext =
    val currentSchema = schema
    SchemaContext(currentSchema, currentSchema)

  override def retrieveAttributeOrTemplateCollection(context: SDLBContext): AttributeCollection | TemplateCollection = context.parentPath.lastOption match
    case Some("type") =>
      // Suggest the values of the type attribute: the type currently written must not narrow the schema
      val schemaContext = navigate(context.parentPath, context.textContext.rootConfig.root(), bestEffort = true).getOrElse(createGlobalSchemaContext)
      schemaContext.generateSchemaSuggestionsForAttributeType
    case _ => retrieveSchemaContext(context, withWordInPath = false) match
      case None => AttributeCollection(Iterable.empty)
      case Some(schemaContext) => schemaContext.generateSchemaSuggestions
  override def retrieveDescription(context: SDLBContext): String = if isWordMeaningless(context.word) then "" else
    retrieveSchemaContext(context, withWordInPath = true) match
      case None =>
        debug("No schema could be retrieved")
        ""
      case Some(schemaContext) =>
        debug(s"Schema retrieved: ${schemaContext.toString.take(300)}")
        schemaContext.getDescription

  /**
   * Not a crucial method but useful to speedup query process and might avoid some unwanted crash
   * @param word word to check
   * @return true if word has no chance to appear in the schema file
   */
  private def isWordMeaningless(word: String): Boolean =
    word.filterNot(Set('{', '}', '[', ']', '"', '(', ')', '#', '/', '\\', '.').contains(_)).isBlank

  private[schema] def retrieveSchemaContext(context: SDLBContext, withWordInPath: Boolean): Option[SchemaContext] =
    val path = if withWordInPath then context.parentPath.appended(context.word) else context.parentPath
    debug(s"path = $path")
    navigate(path, context.textContext.rootConfig.root(), bestEffort = false)

  /**
   * Walks the schema along the path, following the config to find the type of each object.
   * Strict: fails as soon as an element of the path is unknown in the schema.
   * Best effort: stays on the last known schema context, stops where the config ends,
   * and ignores the type of the last object, as it is the one being edited.
   */
  private def navigate(path: List[String], rootConfigValue: ConfigValue, bestEffort: Boolean): Option[SchemaContext] =
    @tailrec
    def loop(remainingPath: List[String], schemaContext: SchemaContext, configValue: ConfigValue): Option[SchemaContext] = remainingPath match
      case Nil => Some(schemaContext)
      case elementPath :: nextPath =>
        val (newConfigValue, oTypeObject) = moveInConfigAndRetrieveType(configValue, elementPath)
        if newConfigValue == null && bestEffort then Some(schemaContext)
        else
          // A missing config value is common while typing: the schema can still be followed by name
          if newConfigValue == null then debug(s"No config value for pathElement=$elementPath and fullPath=$path")
          val byName = schemaContext.updateByName(elementPath)
          val nextSchemaContext = oTypeObject.filterNot(_ => bestEffort && nextPath.size <= 1) match
            case Some(objectType) =>
              val base = byName.getOrElse(schemaContext)
              val byType = base.updateByType(objectType)
              if bestEffort then byType.orElse(Some(base)) else byType
            case None => if bestEffort then byName.orElse(Some(schemaContext)) else byName
          nextSchemaContext match
            case Some(next) => loop(nextPath, next, newConfigValue)
            case None => None
    loop(path, createGlobalSchemaContext, rootConfigValue)

  private[schema] def moveInConfigAndRetrieveType(config: ConfigValue, path: String): (ConfigValue, Option[String]) =
    val newConfig = config match
      case asConfigObject: ConfigObject => asConfigObject.get(path)
      case asConfigList: ConfigList => path.toIntOption.map(index => if index >= 0 && index < asConfigList.size then asConfigList.get(index) else null).getOrElse {
        debug(s"Trying to access an index in config $config but given element path is not of type int: $path")
        config
      }
      case _ =>
        debug(s"Trying to move with config $config while receiving path element $path")
        config

    val objectType = retrieveType(newConfig)
    (newConfig, objectType)

  private def retrieveType(config: ConfigValue): Option[String] = config match
    case asConfigObject: ConfigObject => Option(asConfigObject.get("type")).flatMap(_.unwrapped() match
      case s: String => Some(s)
      case _ => None)
    case _ => None

}