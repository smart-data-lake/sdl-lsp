package io.smartdatalake.schema

import scala.collection.Iterable

object SchemaCollections {
  case class AttributeCollection(attributes: Iterable[SchemaItem])
  case class TemplateCollection(templates: Iterable[(String, Iterable[SchemaItem])])

}
