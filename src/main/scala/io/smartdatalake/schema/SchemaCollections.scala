package io.smartdatalake.schema

import scala.collection.Iterable

object SchemaCollections {
  case class AttributeCollection(attributes: Iterable[SchemaItem])
  case class TemplateCollection(templates: Iterable[Template], templateType: TemplateType)

  /** A whole object of a built-in type, e.g. CopyAction, with its required attributes */
  case class Template(name: String, description: String, attributes: Iterable[SchemaItem])

}
