package io.smartdatalake.schema

import com.typesafe.config.ConfigObject
import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReaderImpl}
import ujson.*

import scala.io.Source
import scala.util.Using

class SchemaReaderSpec extends UnitSpec {

  private val initialContext = SDLBContext.fromText(loadFile("fixture/hocon/with-lists-example.conf"))

  "Schema Reader" should "traverse the config correctly inside element of first element list" in {
    //Simulating path: List(actions, join-departures-airports, transformers, 0, code) as in position 23,6
    val context = initialContext
    val actionContext = context.textContext.rootConfig.getValue("actions")
    val (joinDeparturesAirportsContext, oJoinDeparturesType) = schemaReader.moveInConfigAndRetrieveType(actionContext, "join-departures-airports")
    oJoinDeparturesType shouldBe Some("CustomDataFrameAction")
    val (transformersContext, oTransformersType) = schemaReader.moveInConfigAndRetrieveType(joinDeparturesAirportsContext, "transformers")
    oTransformersType shouldBe None
    val (sqldfTransformerContext, oSqldfType) = schemaReader.moveInConfigAndRetrieveType(transformersContext, "0")
    oSqldfType shouldBe Some("SQLDfsTransformer")
    val (codeContext, oCodeType) = schemaReader.moveInConfigAndRetrieveType(sqldfTransformerContext, "code")
    oCodeType shouldBe None
    codeContext shouldBe a [ConfigObject]
    codeContext.unwrapped().asInstanceOf[java.util.Map[String, String]] should contain key "btl-connected-airports"
  }

  it should "yield the finest schema context given a path inside an attribute of an element in a list" in {
    val context = initialContext.withCaretPosition(23, 6)
    val schemaContext = schemaReader.retrieveSchemaContext(context, withWordInPath = false)
    schemaContext shouldBe defined
    schemaContext.get.localSchema shouldBe a [Obj]
    schemaContext.get.localSchema.obj should contain key "description"
  }

  it should "yield the finest schema context given a path inside an element in a list" in {
    val context = initialContext.withCaretPosition(23, 7)
    val schemaContext = schemaReader.retrieveSchemaContext(context, withWordInPath = false)
    schemaContext shouldBe defined
    schemaContext.get.localSchema shouldBe a [Obj]
    schemaContext.get.localSchema.obj.get("properties").get.obj should have size 6
  }

  it should "yield the finest schema context given a path between elements in a list" in {
    val context = initialContext.withCaretPosition(23, 8)
    val schemaContext = schemaReader.retrieveSchemaContext(context, withWordInPath = false)
    schemaContext shouldBe defined
    schemaContext.get.localSchema shouldBe a [Obj]
    schemaContext.get.localSchema.obj should contain ("type" -> Str("array"))
    schemaContext.get.localSchema.obj should contain key "items"
    schemaContext.get.localSchema.obj.get("items").get.obj.get("oneOf").get.arr should have size 8
  }

  it should "create an empty list of suggestions for an attribute with additionalProperties" in {
    val context = initialContext.withCaretPosition(23, 6)
    schemaReader.retrieveAttributeOrTemplateCollection(context).asInstanceOf[TemplateCollection].templates shouldBe empty
  }

  it should "create a list of attributes when looking for suggestions inside an element in a list" in {
    val context = initialContext.withCaretPosition(23, 7)
    schemaReader.retrieveAttributeOrTemplateCollection(context).asInstanceOf[AttributeCollection].attributes should have size 6
  }

  it should "generate the 4 basic suggestions when caret is at the root level" in {
    val context = initialContext.withCaretPosition(1, 0)
    schemaReader.retrieveAttributeOrTemplateCollection(context).asInstanceOf[AttributeCollection].attributes should have size 4
  }

  it should "find description of actions" in {
    val context = initialContext.withCaretPosition(1, 0)
    schemaReader.retrieveDescription(context) shouldBe "Map of Action name and definition"
  }

  it should "remain quiet when path is wrong" in {
    val context = initialContext.withCaretPosition(9, 4)
    schemaReader.retrieveDescription(context) shouldBe empty
  }

  it should "find description of join-departures-airports as a CustomDataFrameAction object" in {
    val context = initialContext.withCaretPosition(15, 5)
    schemaReader.retrieveDescription(context).take(60) shouldBe "This[[Action]] transforms data between many input and output"
  }

  it should "find description of metadata" in {
    val context = initialContext.withCaretPosition(31, 10)
    schemaReader.retrieveDescription(context) shouldBe "Additional metadata for an Action"
  }

  it should "create a list of Actions attributes when parent is type" in {
    val context = initialContext.withCaretPosition(16, 11)
    val attributes = schemaReader.retrieveAttributeOrTemplateCollection(context).asInstanceOf[AttributeCollection].attributes
    attributes should have size 8
    attributes.map(_.itemType).forall(_ == ItemType.TYPE_VALUE) shouldBe true
    attributes.map(_.name) should contain ("CopyAction")
  }

  it should "create a list of ExecutionMode types when parent is type" in {
    val context = initialContext.withCaretPosition(35, 13)
    val attributes = schemaReader.retrieveAttributeOrTemplateCollection(context).asInstanceOf[AttributeCollection].attributes
    attributes should have size 10
    attributes.map(_.itemType).forall(_ == ItemType.TYPE_VALUE) shouldBe true
    attributes.map(_.name) should contain ("ProcessAllMode")
  }

}
