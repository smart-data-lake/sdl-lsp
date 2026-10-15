package io.smartdatalake.schema

import io.circe.{Json, JsonObject}
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, Template, TemplateCollection}
import io.smartdatalake.schema.SchemaKeywords.*

import scala.annotation.tailrec
import io.smartdatalake.logging.SDLBLogger

/** Pattern matching helpers on circe Json values */
private[schema] object JsonPatterns:
  object JStr:
    def unapply(json: Json): Option[String] = json.asString
  object JObj:
    def unapply(json: Json): Option[JsonObject] = json.asObject
  object JArr:
    def unapply(json: Json): Option[Vector[Json]] = json.asArray

  extension (json: Json)
    /** Field of a JSON object, None if absent or if the value is not an object */
    def field(key: String): Option[Json] = json.asObject.flatMap(_(key))

import JsonPatterns.*

private[schema] case class SchemaContext(private val globalSchema: Json, localSchema: Json) extends SDLBLogger:

  def updateByType(elementType: String): Option[SchemaContext] = update {
    localSchema.field(SCHEMA_TYPE) match
      case None => handleNoSchemaType(elementType)
      case Some(JStr(SCHEMA_OBJECT)) => handleObjectSchemaTypeWithElementType(elementType)
      case Some(JStr(SCHEMA_ARRAY)) => handleArraySchemaTypeWithElementType(elementType)
      // other cases should be invalid because primitive types shouldn't be updated further
      case _ =>
        debug(s"update by type with an abnormal localSchema=$localSchema. elementType=$elementType")
        None
  }
  def updateByName(elementName: String): Option[SchemaContext] = update {
    localSchema.field(SCHEMA_TYPE) match
      case None => None // can only update if type is provided in the user file
      case Some(JStr(SCHEMA_OBJECT)) => handleObjectSchemaTypeWithElementName(elementName)
      case Some(JStr(SCHEMA_ARRAY)) => handleArraySchemaTypeWithElementName(elementName)
      // other cases should be invalid because primitive types shouldn't be updated further
      case _ =>
        debug(s"update by name with an abnormal localSchema=$localSchema. elementName=$elementName")
        None
  }

  def generateSchemaSuggestionsForAttributeType: AttributeCollection =
    val oTemplates = generateSchemaSuggestions
    oTemplates match
      case tc : TemplateCollection => AttributeCollection(tc.templates.map { template =>
        SchemaItem(template.name, ItemType.TYPE_VALUE, template.description, false)
      })
      case ac : AttributeCollection => ac

  def generateSchemaSuggestions: AttributeCollection | TemplateCollection =
    localSchema.field(SCHEMA_TYPE) match
      case Some(JStr(SCHEMA_OBJECT)) =>
        localSchema.field(PROPERTIES) match
          case Some(JObj(properties)) => AttributeCollection(schemaItems(properties, requiredAttributes(localSchema)))
          case _ =>
            val templates = localSchema.field(ADDITIONAL_PROPERTIES)
              .flatMap(_.field(ONE_OF))
              .map(generateTemplates)
              .getOrElse(List.empty)
            TemplateCollection(templates, TemplateType.OBJECT)
      case Some(JStr(SCHEMA_ARRAY)) =>
        val templates = localSchema.field(ITEMS).flatMap(_.field(ONE_OF))
          .map(generateTemplates)
          .getOrElse(List.empty)
        TemplateCollection(templates, TemplateType.ARRAY_ELEMENT)
      case Some(JStr(primitive)) =>
        debug(s"Abnormal localSchema $primitive")
        AttributeCollection(Iterable.empty[SchemaItem])
      case _ =>
        val templates = localSchema.field(ONE_OF)
          .map(generateTemplates)
          .getOrElse(List.empty)
        TemplateCollection(templates, TemplateType.ATTRIBUTES)
  end generateSchemaSuggestions

  def getDescription: String =
    localSchema.field(DESCRIPTION).flatMap(_.asString)
      .orElse(localSchema.field(ADDITIONAL_PROPERTIES).flatMap(_.field(DESCRIPTION)).flatMap(_.asString))
      .getOrElse("")

  private def requiredAttributes(schema: Json): Set[String] =
    schema.field(REQUIRED).flatMap(_.asArray).map(_.flatMap(_.asString).toSet).getOrElse(Set.empty)

  /** Schema items in the order of the schema file */
  private def schemaItems(properties: JsonObject, required: Set[String]): Iterable[SchemaItem] =
    properties.toIterable.map { (attributeName, attributeProperties) =>
      val typeName = attributeProperties.field(ATTRIBUTE_TYPE).flatMap(_.asString).getOrElse(ATTRIBUTE_OBJECT)
      val description = attributeProperties.field(DESCRIPTION).flatMap(_.asString).getOrElse("")
      SchemaItem(attributeName, ItemType.fromName(typeName), description, required.contains(attributeName))
    }

  private def generateTemplates(oneOf: Json): Iterable[Template] =
    oneOf match
      case JArr(array) => array.flatMap(_.field(REF) match
        case Some(JStr(path)) => goToSchemaDefinition(path)
        case _ => None
      ).map {
        case definition @ JObj(_) =>
          val properties = definition.field(PROPERTIES)
          val objectName = properties.flatMap(_.field(ATTRIBUTE_TYPE)).flatMap(_.field(SCHEMA_CONST)).flatMap(_.asString)
            .getOrElse(DEFAULT_PROPERTY_NAME)
          val items = properties match
            case Some(JObj(values)) => schemaItems(values, requiredAttributes(definition))
            case _ => Iterable.empty[SchemaItem]
          val description = definition.field(DESCRIPTION).flatMap(_.asString).getOrElse("")
          Template(objectName, description, items.filter(_.required))
        case _ => Template("", "", Iterable.empty[SchemaItem])
      }
      case _ => Iterable.empty

  private def update(body: => Option[Json]): Option[SchemaContext] =
    body.flatMap(flattenRef).map(schema => copy(localSchema = schema))

  private def flattenRef(schema: Json): Option[Json] = schema match
    case JObj(_) => schema.field(REF) match
      case Some(JStr(path)) => goToSchemaDefinition(path).flatMap(flattenRef)
      case _ => Some(schema)
    case _ =>
      debug(s"abnormal schema when flattening at the end: $schema")
      None
  private def handleNoSchemaType(elementType: String): Option[Json] =
    localSchema.field(ONE_OF).flatMap(findElementTypeWithOneOf(_, elementType))

  private def findElementTypeWithOneOf(oneOf: Json, elementType: String): Option[Json] = oneOf match
    case JArr(array) =>
      val path = array.flatMap(_.field(REF)).flatMap(_.asString)
        .find(_.split(SCHEMA_PATH_SEPARATOR).last == elementType)
      path match
        case Some(path) => goToSchemaDefinition(path)
        case _ =>
          debug(s"no path found with elementType=$elementType in oneOf=$oneOf")
          None
    case _ =>
      warn(s"Attempt to find element type with oneOf, but it is not an array: $oneOf")
      None
  private def handleObjectSchemaTypeWithElementType(elementType: String): Option[Json] =
    localSchema.field(ADDITIONAL_PROPERTIES)
      .flatMap(_.field(ONE_OF))
      .flatMap(findElementTypeWithOneOf(_, elementType))
  private def handleObjectSchemaTypeWithElementName(elementName: String): Option[Json] =
    // None can be a common case, when trying to move with "byName" even though a type in the config is defined
    localSchema.field(PROPERTIES).flatMap(_.field(elementName))
  private def handleArraySchemaTypeWithElementType(elementType: String): Option[Json] =
    localSchema.field(ITEMS)
      .flatMap(_.field(ONE_OF))
      .flatMap(findElementTypeWithOneOf(_, elementType))
  private def handleArraySchemaTypeWithElementName(elementName: String): Option[Json] =
    val itemSchema = localSchema.field(ITEMS)
    itemSchema.flatMap(_.field(SCHEMA_TYPE)) match
      case Some(JStr(SCHEMA_OBJECT)) => itemSchema
        .flatMap(_.field(PROPERTIES))
        .flatMap(_.field(elementName))
      case Some(JStr(SCHEMA_ARRAY)) =>
        warn(s"request to handle Array Schema with element type array itself $localSchema")
        None
      case _ => None // This can be a common case, when trying to move with "byName" even though a type in the config is defined

  private def goToSchemaDefinition(path: String): Option[Json] =
    @tailrec
    def applyPathSegment(remainingPath: List[String], oCurrentSchema: Option[Json]): Option[Json] = remainingPath match
      case Nil => oCurrentSchema
      case pathSegment::newRemainingPath => applyPathSegment(newRemainingPath, oCurrentSchema.flatMap(_.field(pathSegment)))

    val splitPath = path.split(SCHEMA_PATH_SEPARATOR).toList.tail
    applyPathSegment(splitPath, Some(globalSchema))
