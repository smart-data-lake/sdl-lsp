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

class SchemaReaderImpl(val schemaPath: String) extends SchemaReader {

  private val logger = LoggerFactory.getLogger(getClass)
  private val schema = ujson.read(Using.resource(getClass.getClassLoader.getResourceAsStream(schemaPath)) { inputStream =>
    Source.fromInputStream(inputStream).getLines().mkString("\n").trim
  })


  private[schema] def createGlobalSchemaContext: SchemaContext = SchemaContext(schema, schema)

  override def retrieveAttributeOrTemplateCollection(context: SDLBContext): AttributeCollection | TemplateCollection = retrieveSchemaContext(context, withWordInPath = false) match
    case None => AttributeCollection(Iterable.empty)
    case Some(schemaContext) => schemaContext.generateSchemaSuggestions
  override def retrieveDescription(context: SDLBContext): String = if isWordMeaningless(context.word) then "" else
    retrieveSchemaContext(context, withWordInPath = true) match
      case None => ""
      case Some(schemaContext) => schemaContext.getDescription

  /**
   * Not a crucial method but useful to speedup query process and might avoid some unwanted crash
   * @param word word to check
   * @return true if word has no chance to appear in the schema file
   */
  private def isWordMeaningless(word: String): Boolean =
    word.filterNot(Set('{', '}', '[', ']', '"', '(', ')', '#', '/', '\\', '.').contains(_)).isBlank

  private[schema] def retrieveSchemaContext(context: SDLBContext, withWordInPath: Boolean): Option[SchemaContext] =
    val rootConfig = context.textContext.rootConfig
    val path = if withWordInPath then context.parentPath.appended(context.word) else context.parentPath
    path match
      case Nil => Some(createGlobalSchemaContext)
      case globalObject::remainingPath =>
        val schemaContext = createGlobalSchemaContext.updateByName(globalObject)
        val rootConfigValue = rootConfig.getValue(globalObject)
        remainingPath.foldLeft((schemaContext, rootConfigValue)){(scCv, elementPath) =>
          val (newConfigValue, oTypeObject) = moveInConfigAndRetrieveType(scCv._2, elementPath)
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
        logger.debug("Trying to access an index in config {} but given element path is not of type int: {}", config, path)
        config
      }
      case _ =>
        logger.debug("Trying to move with config {} while receiving path element {}", config, path)
        config

    val objectType = retrieveType(newConfig)
    if (newConfig == null) {logger.error("Error, newConfig is null with path={}, config={}", path, config)}
    (newConfig, objectType)

  private def retrieveType(config: ConfigValue): Option[String] = config match
    case asConfigObject: ConfigObject => Option(asConfigObject.get("type")).flatMap(_.unwrapped() match
      case s: String => Some(s)
      case _ => None)
    case _ => None


}