package io.smartdatalake.schema

import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import io.smartdatalake.schema.SchemaKeywords.*
import org.slf4j.LoggerFactory
import ujson.{Arr, Obj, Str}
import ujson.Value.Value

import scala.annotation.tailrec
private[schema] case class SchemaContext(private val globalSchema: Value, localSchema: Value):
  private val logger = LoggerFactory.getLogger(getClass)

  def updateByType(elementType: String): Option[SchemaContext] = update {
    localSchema.obj.get(SCHEMA_TYPE) match
      case None => handleNoSchemaType(elementType)
      case Some(Str(SCHEMA_OBJECT)) => handleObjectSchemaTypeWithElementType(elementType)
      case Some(Str(SCHEMA_ARRAY)) => handleArraySchemaTypeWithElementType(elementType)
      // other cases should be invalid because primitive types shouldn't be updated further
      case _ =>
        logger.debug("update by type with an abnormal localSchema {}. elementType={}", localSchema, elementType)
        None
  }
  def updateByName(elementName: String): Option[SchemaContext] = update {
    localSchema.obj.get(SCHEMA_TYPE) match
      case None => None // can only update if type is provided in the user file
      case Some(Str(SCHEMA_OBJECT)) => handleObjectSchemaTypeWithElementName(elementName)
      case Some(Str(SCHEMA_ARRAY)) => handleArraySchemaTypeWithElementName(elementName)
      // other cases should be invalid because primitive types shouldn't be updated further
      case _ =>
        logger.debug("update by name with an abnormal localSchema {}. elementName={}", localSchema, elementName)
        None
  }

  def generateSchemaSuggestions: AttributeCollection | TemplateCollection =
    val asObject = localSchema.obj
    asObject.get(SCHEMA_TYPE) match
      case Some(Str(SCHEMA_OBJECT)) =>
        val properties = asObject.get(PROPERTIES)
        val required = asObject.get(REQUIRED).map(_.arr.toSet).getOrElse(Set.empty)
        properties match
          case Some(Obj(values)) => AttributeCollection(values.map { case (attributeName, attributeProperties) =>
            val typeName = attributeProperties.obj.get(ATTRIBUTE_TYPE).map(_.str).getOrElse(ATTRIBUTE_OBJECT)
            val description = attributeProperties.obj.get(DESCRIPTION).map(_.str).getOrElse("")
            SchemaItem(attributeName, ItemType.fromName(typeName), description, required.contains(attributeName))
          })
          case _ =>
            val templates = asObject.get(ADDITIONAL_PROPERTIES)
              .flatMap(_.obj.get(ONE_OF))
              .map(generateTemplates)
              .getOrElse(List.empty)
            TemplateCollection(templates, TemplateType.OBJECT)
      case Some(Str(SCHEMA_ARRAY)) =>
        val templates = asObject(ITEMS).obj.get(ONE_OF)
          .map(generateTemplates)
          .getOrElse(List.empty)
        TemplateCollection(templates, TemplateType.ARRAY_ELEMENT)
      case Some(Str(primitive)) =>
        logger.debug("Abnormal localSchema {}", primitive)
        AttributeCollection(Iterable.empty[SchemaItem])
      case _ =>
        val templates = asObject.get(ONE_OF)
          .map(generateTemplates)
          .getOrElse(List.empty)
        TemplateCollection(templates, TemplateType.ATTRIBUTES)
  end generateSchemaSuggestions
  
  def getDescription: String =
    localSchema.obj.get(DESCRIPTION)
      .map(_.str)
      .orElse(localSchema.obj.get(ADDITIONAL_PROPERTIES)
        .flatMap{
          case Obj(value) => value.get(DESCRIPTION).map(_.str)
          case _ => None
        })
      .getOrElse("")
  private def generateTemplates(oneOf: Value): Iterable[(String, Iterable[SchemaItem])] =
    oneOf match
      case Arr(array) => array.map(_.obj.get(REF) match
        case Some(Str(path)) => goToSchemaDefinition(path)
        case _ => None
      ).flatMap(_.map {
        case Obj(obj) =>
          val properties = obj.get(PROPERTIES)
          val required = obj.get(REQUIRED).map(_.arr.toSet).getOrElse(Set.empty)
          val objectName = properties.flatMap(_.obj.get(ATTRIBUTE_TYPE)).flatMap(_.obj.get(SCHEMA_CONST)).flatMap {
            case Str(oName) => Some(oName)
            case _ => None
          }.getOrElse(DEFAULT_PROPERTY_NAME)
          val items = properties match
            case Some(Obj(values)) => values.map { case (attributeName, attributeProperties) =>
              val typeName = attributeProperties.obj.get(ATTRIBUTE_TYPE).map(_.str).getOrElse(ATTRIBUTE_OBJECT)
              val description = attributeProperties.obj.get(DESCRIPTION).map(_.str).getOrElse("")
              SchemaItem(attributeName, ItemType.fromName(typeName), description, required.contains(attributeName))
            }
            case _ => Iterable.empty[SchemaItem]
          (objectName, items.filter(_.required))
        case _ => ("", Iterable.empty[SchemaItem])

      })
      case _ => Iterable.empty
  private def update(body: => Option[Value]): Option[SchemaContext] =
    body.flatMap(flattenRef).map(schema => copy(localSchema = schema))



  private def flattenRef(schema: Value): Option[Value] = schema match
    case Obj(obj) => obj.get(REF) match
      case Some(Str(path)) => goToSchemaDefinition(path).flatMap(flattenRef)
      case _ => Some(schema)
    case _ =>
      logger.debug("abnormal schema when flattening at the end: {}", schema)
      None
  private def handleNoSchemaType(elementType: String): Option[Value] =
    localSchema.obj.get(ONE_OF).flatMap(findElementTypeWithOneOf(_, elementType))

  private def findElementTypeWithOneOf(oneOf: Value, elementType: String): Option[Value] = oneOf match
    case Arr(array) =>
      val path = array.arr.toSet.find {
        case Obj(refPath) => refPath.get(REF).exists {
          case Str(path) => path.split(SCHEMA_PATH_SEPARATOR).last == elementType
          case _ => false
        }
        case _ => false
      }.flatMap(_.obj.get(REF))
      path match
        case Some(Str(path)) => goToSchemaDefinition(path)
        case _ =>
          logger.debug("no path found with elementType={} in oneOf={}", elementType, oneOf)
          None
    case _ =>
      logger.warn("Attempt to find element type with oneOf, but it is not an array: {}", oneOf)
      None
  private def handleObjectSchemaTypeWithElementType(elementType: String): Option[Value] =
    localSchema.obj.get(ADDITIONAL_PROPERTIES)
      .flatMap(_.obj.get(ONE_OF))
      .flatMap(findElementTypeWithOneOf(_, elementType))
  private def handleObjectSchemaTypeWithElementName(elementName: String): Option[Value] =
    localSchema.obj.get(PROPERTIES) match
      case Some(Obj(values)) => values.get(elementName)
      case _ => None // This can be a common case, when trying to move with "byName" even though a type in the config is defined
  private def handleArraySchemaTypeWithElementType(elementType: String): Option[Value] =
    localSchema.obj.get(ITEMS)
      .flatMap(_.obj.get(ONE_OF))
      .flatMap(findElementTypeWithOneOf(_, elementType))
  private def handleArraySchemaTypeWithElementName(elementName: String): Option[Value] =
    val itemSchema = localSchema.obj.get(ITEMS)
    itemSchema.flatMap(_.obj.get(SCHEMA_TYPE)) match
      case Some(Str(SCHEMA_OBJECT)) => itemSchema
        .flatMap(_.obj.get(PROPERTIES))
        .flatMap(_.obj.get(elementName))
      case Some(Str(SCHEMA_ARRAY)) =>
        logger.warn("request to handle Array Schema with element type array itself {}", localSchema)
        None
      case _ => None // This can be a common case, when trying to move with "byName" even though a type in the config is defined

  private def goToSchemaDefinition(path: String): Option[ujson.Value.Value] =
    @tailrec
    def applyPathSegment(remainingPath: List[String], oCurrentSchema: Option[ujson.Value.Value]): Option[ujson.Value.Value] = remainingPath match
      case Nil => oCurrentSchema
      case pathSegment::newRemainingPath => applyPathSegment(newRemainingPath, oCurrentSchema.flatMap(currentSchema => currentSchema.obj.get(pathSegment)))

    val splitPath = path.split(SCHEMA_PATH_SEPARATOR).toList.tail
    applyPathSegment(splitPath, Some(globalSchema))



