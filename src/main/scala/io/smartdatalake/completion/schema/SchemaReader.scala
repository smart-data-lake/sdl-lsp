package io.smartdatalake.completion.schema

trait SchemaReader:
  def retrieveActionProperties(typeName: String): Iterable[SchemaItem]
