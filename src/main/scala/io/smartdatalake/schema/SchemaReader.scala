package io.smartdatalake.schema

trait SchemaReader:
  def retrieveActionProperties(typeName: String): Iterable[SchemaItem]
  
  def retrieveActionPropertyDescription(typeName: String, propertyName: String): String
  
  def retrieveActionTypesWithRequiredAttributes(): Iterable[(String, Iterable[SchemaItem])]
