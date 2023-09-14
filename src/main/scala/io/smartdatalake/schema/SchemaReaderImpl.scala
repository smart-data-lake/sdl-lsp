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

  override def retrieveAttributeOrTemplateCollection(context: SDLBContext): AttributeCollection | TemplateCollection = retrieveSchemaContext(context) match
    case None => AttributeCollection(Iterable.empty)
    case Some(schemaContext) => schemaContext.generateSchemaSuggestions
  private[schema] def retrieveSchemaContext(context: SDLBContext): Option[SchemaContext] =
    val rootConfig = context.textContext.rootConfig
    val parentPath = context.parentPath
    parentPath match
      case Nil => None
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



  private[schema] def moveInConfigAndRetrieveType(config: ConfigValue, path: String): (ConfigValue, Option[String]) = //TODO what about a path finishing with "type"
    val newConfig = config match
      case asConfigObject: ConfigObject => asConfigObject.get(path)
      case asConfigList: ConfigList => asConfigList.get(path.toInt)
      case _ =>
        logger.debug("trying to move with config {} while receiving path element {}", config, path)
        config //TODO return config itself?

    val objectType = retrieveType(newConfig)
    (newConfig, objectType)

  private def retrieveType(config: ConfigValue): Option[String] = config match
    case asConfigObjectAgain: ConfigObject => Option(asConfigObjectAgain.get("type")).flatMap(_.unwrapped() match
      case s: String => Some(s)
      case _ => None)
    case _ => None


}