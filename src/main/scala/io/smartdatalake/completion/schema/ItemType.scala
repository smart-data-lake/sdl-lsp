package io.smartdatalake.completion.schema

enum ItemType(val name: String) {
  case STRING extends ItemType("string")
  case BOOLEAN extends ItemType("boolean")
  case INTEGER extends ItemType("integer")
  case OBJECT extends ItemType("object")
  case ARRAY extends ItemType("array")
  
  def isPrimitiveValue: Boolean = this == ItemType.STRING || this == ItemType.BOOLEAN || this == ItemType.INTEGER
  
  def isComplexValue: Boolean = this == ItemType.OBJECT || this == ItemType.ARRAY
  
}

object ItemType:
  def fromName(name: String): ItemType = name match
    case "string" => ItemType.STRING
    case "boolean" => ItemType.BOOLEAN
    case "integer" => ItemType.INTEGER
    case "object" => ItemType.OBJECT
    case "array" => ItemType.ARRAY