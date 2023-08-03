package io.smartdatalake.completion.schema

import scala.io.Source
import scala.util.Using

class SchemaReaderImpl(val schemaPath: String) extends SchemaReader {

  private val schema = ujson.read(Using.resource(getClass.getClassLoader.getResourceAsStream(schemaPath)) { inputStream =>
    Source.fromInputStream(inputStream).getLines().mkString("\n").trim
  })

  override def retrieveActionProperties(typeName: String): Iterable[SchemaItem] =
    val properties = schema("definitions")("Action")(typeName)("properties")
    
    properties.obj.map { case (keyName, value) =>
      val typeName = value.obj.get("type").map(_.str).getOrElse("string")
      val description = value.obj.get("description").map(_.str).getOrElse("")
      SchemaItem(keyName, ItemType.fromName(typeName), description)
    }


}
