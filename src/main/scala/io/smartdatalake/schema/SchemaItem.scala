package io.smartdatalake.schema

case class SchemaItem(name: String, itemType: ItemType, description: String, required: Boolean):
  override def toString: String = s"SchemaItem(${name.take(15)}, $itemType, ${description.take(15)}, $required)"
