package io.smartdatalake.schema

import com.typesafe.config.{ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import org.slf4j.LoggerFactory
import ujson.Value.Value
import ujson.{Arr, Bool, Null, Num, Obj, Str}

import scala.annotation.tailrec
import scala.io.Source
import scala.util.{Try, Using}
import io.smartdatalake.logging.SDLBLogger

class SchemaReaderImpl(val schemaPath: String) extends SchemaReader with SDLBLogger {

  private val schema = ujson.read(Using.resource(getClass.getClassLoader.getResourceAsStream(schemaPath)) { inputStream =>
    Source.fromInputStream(inputStream).getLines().mkString("\n").trim
  })


  private[schema] def createGlobalSchemaContext: SchemaContext = SchemaContext(schema, schema)

  override def retrieveAttributeOrTemplateCollection(context: SDLBContext): AttributeCollection | TemplateCollection = context.parentPath.lastOption match
    case Some("type") =>
      val schemaContext = retrieveSchemaContextForTypeAttribute(context)
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

  private def retrieveSchemaContextForTypeAttribute(context: SDLBContext): SchemaContext =
    @tailrec
    def moveInBestEffort(path: List[String], schemaContext: SchemaContext, configValue: ConfigValue): SchemaContext = path match
      case Nil => schemaContext
      case elementPath::remainingPath =>
        val (newConfigValue, oTypeObject) = moveInConfigAndRetrieveType(configValue, elementPath)
        if newConfigValue == null then schemaContext else
          val tryUpdateByName = schemaContext.updateByName(elementPath).getOrElse(schemaContext)
          oTypeObject match
            case Some(objectType) =>
              if remainingPath.size > 1 then // don't take the last type into account
                moveInBestEffort(remainingPath, tryUpdateByName.updateByType(objectType).getOrElse(tryUpdateByName), newConfigValue)
              else
                moveInBestEffort(remainingPath, tryUpdateByName, newConfigValue)
            case None =>
              moveInBestEffort(remainingPath, tryUpdateByName, newConfigValue)
    end moveInBestEffort

    val path = context.parentPath
    val initialSchemaContext = createGlobalSchemaContext
    val rootConfigValue: ConfigValue = context.textContext.rootConfig.root()
    moveInBestEffort(path, initialSchemaContext, rootConfigValue)

  private[schema] def retrieveSchemaContext(context: SDLBContext, withWordInPath: Boolean): Option[SchemaContext] =
    val path = if withWordInPath then context.parentPath.appended(context.word) else context.parentPath
    val oInitialSchemaContext: Option[SchemaContext] = Some(createGlobalSchemaContext)
    val rootConfigValue: ConfigValue = context.textContext.rootConfig.root()
    debug(s"path = $path")
    path.foldLeft((oInitialSchemaContext, rootConfigValue)){(scCv, elementPath) =>
      val (newConfigValue, oTypeObject) = moveInConfigAndRetrieveType(scCv._2, elementPath)
      if (newConfigValue == null) {warn(s"Error, newConfig is null with pathElement=$elementPath and fullPath=$path")}
      val newSchemaContext = oTypeObject match
        case Some(objectType) =>
          val tryUpdateByName = scCv._1.flatMap(_.updateByName(elementPath))
          tryUpdateByName.orElse(scCv._1).flatMap(_.updateByType(objectType))
        case None => scCv._1.flatMap(_.updateByName(elementPath))
      (newSchemaContext, newConfigValue)
    }._1
  end retrieveSchemaContext

  private[schema] def moveInConfigAndRetrieveType(config: ConfigValue, path: String): (ConfigValue, Option[String]) =
    val newConfig = config match
      case asConfigObject: ConfigObject => asConfigObject.get(path)
      case asConfigList: ConfigList => path.toIntOption.map(asConfigList.get).getOrElse {
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