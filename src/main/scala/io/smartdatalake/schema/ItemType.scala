package io.smartdatalake.schema

enum ItemType(val name: String, val defaultValue: String) {
  case STRING extends ItemType("string", "\"???\"")
  case BOOLEAN extends ItemType("boolean", "true")
  case INTEGER extends ItemType("integer", "0")
  case OBJECT extends ItemType("object", "{}")
  case ARRAY extends ItemType("array", "[]")
  
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