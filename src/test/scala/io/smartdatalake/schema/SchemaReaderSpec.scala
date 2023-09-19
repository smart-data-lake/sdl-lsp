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
    println(context.parentPath.appended(context.word))
    println(schemaReader.retrieveDescription(context))
  }

  it should "debug" in {
    val contextText =
      "\nactions {\n\n  join-departures-airports {\n    type \u003d CustomDataFrameAction\n    \n    inputIds \u003d [stg-departures, int-airports, dataobjectsexporterdataobject_PLACEHOLDER]\n    transformer \u003d {\n\n      code \u003d {\n        btl-connected-airports \u003d \"select stg_departures.estdepartureairport, stg_departures.estarrivalairport,        airports.*         from stg_departures join int_airports airports on stg_departures.estArrivalAirport \u003d airports.ident\"\n      }\n    }\n  }\n\n  compute-distances {\n    type \u003d CopyAction\n    transformers \u003d [\n       {\n\t\ttype \u003d PythonCodeDfTransformer\n\t},\n       {\n\t\ttype \u003d BlacklistTransformer\n         description \u003d \"???\"\n\t\tcolumnBlacklist \u003d []\n\t}\n\n    ]\n    executionMode {\n        \n\t\ttype \u003d DataObjectStateIncrementalMode\n    \n    }\n    inputId \u003d \"???\"\n    code \u003d {\n      btl-departures-arrivals-airports \u003d \"select btl_connected_airports.estdepartureairport, btl_connected_airports.estarrivalairport,        btl_connected_airports.name as arr_name, btl_connected_airports.latitude_deg as arr_latitude_deg, btl_connected_airports.longitude_deg as arr_longitude_deg,        airports.name as dep_name, airports.latitude_deg as dep_latitude_deg, airports.longitude_deg as dep_longitude_deg           from btl_connected_airports join int_airports airports on btl_connected_airports.estdepartureairport \u003d airports.ident\"\n    }\n    metadata {\n      feed \u003d compute\n    }\n  }\n\n  historizeaction_PLACEHOLDER {\n\t\ttype \u003d HistorizeAction\n\t\tinputId \u003d \"???\"\n\t\toutputId \u003d \"???\"\n\t}\n\n  download-airports  {\n    \n    inputId \u003d ext-airports\n  }\n  \n}\n\ndataObjects {\n  dataobjectsexporterdataobject_PLACEHOLDER {\n    type \u003d DataObjectsExporterDataObject\n  }\n\n  csvfiledataobject_PLACEHOLDER {\n    type \u003d CsvFileDataObject\n    path \u003d \"???\"\n  }\n\n}\n\n\n"
    val context = SDLBContext.fromText(contextText).withCaretPosition(7, 22) //or 52
    println(context)
  }
  
  //TODO add tests for description
}
