package io.smartdatalake.schema

import org.slf4j.LoggerFactory

enum ItemType(val name: String, val defaultValue: String) {
  case STRING extends ItemType("string", "\"???\"")
  case BOOLEAN extends ItemType("boolean", "true")
  case INTEGER extends ItemType("integer", "0")
  case OBJECT extends ItemType("object", "{}")
  case ARRAY extends ItemType("array", "[]")
  case TYPE_VALUE extends ItemType("type", "")
  
  def isPrimitiveValue: Boolean = this == ItemType.STRING || this == ItemType.BOOLEAN || this == ItemType.INTEGER
  
  def isComplexValue: Boolean = this == ItemType.OBJECT || this == ItemType.ARRAY
  
}

object ItemType:
  private val logger = LoggerFactory.getLogger(ItemType.getClass)
  def fromName(name: String): ItemType = name match
    case "string" => ItemType.STRING
    case "boolean" => ItemType.BOOLEAN
    case "integer" => ItemType.INTEGER
    case "object" => ItemType.OBJECT
    case "array" => ItemType.ARRAY
    case _ => 
      logger.warn("Attempt to translate unknown type: {}", name)
      ItemType.STRING
      