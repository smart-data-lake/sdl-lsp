package io.smartdatalake.schema

import scala.io.Source
import scala.util.Using

class SchemaReaderImpl(val schemaPath: String) extends SchemaReader {

  private val schema = ujson.read(Using.resource(getClass.getClassLoader.getResourceAsStream(schemaPath)) { inputStream =>
    Source.fromInputStream(inputStream).getLines().mkString("\n").trim
  })


  override def retrieveActionPropertyDescription(typeName: String, propertyName: String): String =
    schema("definitions")("Action").obj.get(typeName)
      .flatMap(typeContext => typeContext("properties").obj.get(propertyName))
      .flatMap(property => property.obj.get("description").map(_.str)).getOrElse("")

  override def retrieveActionProperties(typeName: String): Iterable[SchemaItem] =
    schema("definitions")("Action").obj.get(typeName) match
      case None => Iterable.empty[SchemaItem]
      case Some(typeContext) =>
        val properties = typeContext("properties")
        val required = typeContext("required").arr.toSet

        properties.obj.map { case (keyName, value) =>
          val typeName = value.obj.get("type").map(_.str).getOrElse("string")
          val description = value.obj.get("description").map(_.str).getOrElse("")
          SchemaItem(keyName, ItemType.fromName(typeName), description, required.contains(keyName))
        }

  private lazy val actionsWithProperties: Iterable[(SchemaItem, Iterable[SchemaItem])] =
    for (actionType, attributes) <- schema("definitions")("Action").obj
      yield (SchemaItem(actionType, ItemType.OBJECT, attributes.obj.get("description").map(_.str).getOrElse(""), false),
        retrieveActionProperties(actionType))

  override def retrieveActionTypesWithRequiredAttributes(): Iterable[(String, Iterable[SchemaItem])] =
    actionsWithProperties.map{ (item, attributes) => (item.name, attributes.filter(_.required)) }

}
