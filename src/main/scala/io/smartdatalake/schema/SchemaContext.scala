package io.smartdatalake.schema

import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import org.slf4j.LoggerFactory
import ujson.{Arr, Obj, Str}
import ujson.Value.Value

import scala.annotation.tailrec
private[schema] case class SchemaContext(private val globalSchema: Value, private[schema] val localSchema: Value):
  //TODO refactor all those string literals

  private val logger = LoggerFactory.getLogger(getClass)

  def updateByType(elementType: String): Option[SchemaContext] = update {
    localSchema.obj.get("type") match
      case None => handleNoSchemaType(elementType)
      case Some(Str("object")) => handleObjectSchemaTypeWithElementType(elementType)
      case Some(Str("array")) => handleArraySchemaTypeWithElementType(elementType)
      // other cases should be invalid because primitive types shouldn't be updated further
      case _ =>
        logger.debug("update by type with an abnormal localSchema {}. elementType={}", localSchema, elementType)
        None
  }
  def updateByName(elementName: String): Option[SchemaContext] = update {
    localSchema.obj.get("type") match
      case None => None // can only update if type is provided in the user file
      case Some(Str("object")) => handleObjectSchemaTypeWithElementName(elementName)
      case Some(Str("array")) => handleArraySchemaTypeWithElementName(elementName)
      // other cases should be invalid because primitive types shouldn't be updated further
      case _ =>
        logger.debug("update by name with an abnormal localSchema {}. elementName={}", localSchema, elementName)
        None
  }

  def generateSchemaSuggestions: AttributeCollection | TemplateCollection =
    val asObject = localSchema.obj
    asObject.get("type") match
      case Some(Str("object")) =>
        val properties = asObject.get("properties")
        val required = asObject.get("required").map(_.arr.toSet).getOrElse(Set.empty)
        properties match
          case Some(Obj(values)) => AttributeCollection(values.map { case (attributeName, attributeProperties) =>
            val typeName = attributeProperties.obj.get("type").map(_.str).getOrElse("object")
            val description = attributeProperties.obj.get("description").map(_.str).getOrElse("")
            SchemaItem(attributeName, ItemType.fromName(typeName), description, required.contains(attributeName))
          })
          case _ => asObject.get("additionalProperties").flatMap(_.obj.get("oneOf")) match
            case Some(oneOf) => generateTemplates(oneOf)
            case _ => AttributeCollection(Iterable.empty[SchemaItem])
      case Some(Str("array")) =>
        val itemSchema = asObject("items")
        itemSchema.obj.get("oneOf") match
          case Some(oneOf) => generateTemplates(oneOf)
          case None => AttributeCollection(Iterable.empty[SchemaItem])
      case Some(Str(primitive)) =>
        logger.debug("Abnormal localSchema {}", primitive)
        AttributeCollection(Iterable.empty[SchemaItem])
      case _ =>
        AttributeCollection(Iterable.empty[SchemaItem]) // TODO handle oneOf here

  private def generateTemplates(oneOf: Value) =
    val res = oneOf match
      case Arr(array) => array.map(_.obj.get("$ref") match
        case Some(Str(path)) => goToSchemaDefinition(path)
        case _ => None
      ).flatMap(_.map {
        case Obj(obj) =>
          val properties = obj.get("properties")
          val required = obj.get("required").map(_.arr.toSet).getOrElse(Set.empty)
          val objectName = properties.flatMap(_.obj.get("type")).flatMap(_.obj.get("const")).flatMap {
            case Str(oName) => Some(oName)
            case _ => None
          }.getOrElse("$PROPERTY")
          val items = properties match
            case Some(Obj(values)) => values.map { case (attributeName, attributeProperties) =>
              val typeName = attributeProperties.obj.get("type").map(_.str).getOrElse("object")
              val description = attributeProperties.obj.get("description").map(_.str).getOrElse("")
              SchemaItem(attributeName, ItemType.fromName(typeName), description, required.contains(attributeName))
            }
            case _ => Iterable.empty[SchemaItem]
          (objectName, items.filter(_.required))
        case _ => ("", Iterable.empty[SchemaItem])

      })
      case _ => Iterable.empty
    TemplateCollection(res)
  private def update(body: => Option[Value]): Option[SchemaContext] =
    body.flatMap(flattenRef).map(schema => copy(localSchema = schema))



  private def flattenRef(schema: Value): Option[Value] = schema match
    case Obj(obj) => obj.get("$ref") match
      case Some(Str(path)) => goToSchemaDefinition(path).flatMap(flattenRef)
      case _ => Some(schema)
    case _ =>
      logger.debug("abnormal schema when flattening at the end: {}", schema)
      None
  private def handleNoSchemaType(elementType: String): Option[Value] =
    localSchema.obj.get("oneOf").flatMap(findElementTypeWithOneOf(_, elementType))

  private def findElementTypeWithOneOf(oneOf: Value, elementType: String): Option[Value] = oneOf match
    case Arr(array) =>
      val path = array.arr.toSet.find {
        case Obj(refPath) => refPath.get("$ref").exists {
          case Str(path) => path.split("/").last == elementType
          case _ => false
        }
        case _ => false
      }.flatMap(_.obj.get("$ref"))
      path match
        case Some(Str(path)) => goToSchemaDefinition(path)
        case _ =>
          logger.debug("no path found with elementType={} in oneOf={}", elementType, oneOf)
          None
    case _ =>
      logger.warn("Attempt to find element type with oneOf, but it is not an array: {}", oneOf)
      None
  private def handleObjectSchemaTypeWithElementType(elementType: String): Option[Value] =
    localSchema.obj.get("additionalProperties")
      .flatMap(_.obj.get("oneOf"))
      .flatMap(findElementTypeWithOneOf(_, elementType))
  private def handleObjectSchemaTypeWithElementName(elementName: String): Option[Value] =
    localSchema.obj.get("properties") match
      case Some(Obj(values)) => values.get(elementName)
      case _ =>
        logger.debug("Attempt to handle object schema with element name, but properties not found in {} with elementName={}", localSchema, elementName)
        None
  private def handleArraySchemaTypeWithElementType(elementType: String): Option[Value] =
    localSchema.obj.get("items")
      .flatMap(_.obj.get("oneOf"))
      .flatMap(findElementTypeWithOneOf(_, elementType))
  private def handleArraySchemaTypeWithElementName(elementName: String): Option[Value] =
    val itemSchema = localSchema.obj.get("items")
    itemSchema.flatMap(_.obj.get("type")) match
      case Some(Str("object")) => itemSchema
        .flatMap(_.obj.get("properties"))
        .flatMap(_.obj.get(elementName))
      case Some(Str("array")) =>
        logger.warn("request to handle Array Schema with element type array itself {}", localSchema)
        None
      case _ =>
        logger.warn("no type found for itemSchema with localSchema={}", localSchema)
        None

  private def goToSchemaDefinition(path: String): Option[ujson.Value.Value] =
    @tailrec
    def applyPathSegment(remainingPath: List[String], oCurrentSchema: Option[ujson.Value.Value]): Option[ujson.Value.Value] = remainingPath match
      case Nil => oCurrentSchema
      case pathSegment::newRemainingPath => applyPathSegment(newRemainingPath, oCurrentSchema.flatMap(currentSchema => currentSchema.obj.get(pathSegment)))

    val splitPath = path.split("/").toList.tail
    applyPathSegment(splitPath, Some(globalSchema))



