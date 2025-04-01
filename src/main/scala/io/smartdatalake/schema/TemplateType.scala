package io.smartdatalake.schema

enum TemplateType:
  case 
    OBJECT, // Object is recognized, attributes are suggested
    ARRAY_ELEMENT, // Array is recognized, elements are suggested (can be string, object, etc.)
    ATTRIBUTES // Anonymous object, possible object definitions are suggested. See "executionMode" of "CustomDataFrameAction" for example