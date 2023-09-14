package io.smartdatalake.schema

import io.smartdatalake.UnitSpec
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReaderImpl}
import ujson.*

import scala.io.Source
import scala.util.Using

class SchemaContextSpec extends UnitSpec {
  // Level 0
  private lazy val initialSchemaContext = schemaReader.createGlobalSchemaContext

  // Level 1
  private lazy val actionSchemaContext = initialSchemaContext.updateByName("actions")

  // Level 2
  private lazy val specificActionSchemaContext = actionSchemaContext.flatMap(_.updateByType("CopyAction"))
  private lazy val unknownActionSchemaContext = actionSchemaContext.flatMap(_.updateByType(""))

  // Level 3
  private lazy val copyActionMetaDataSchemaContext = specificActionSchemaContext.flatMap(_.updateByName("metadata"))
  private lazy val copyActionInputIdSchemaContext = specificActionSchemaContext.flatMap(_.updateByName("inputId"))
  private lazy val copyActionExecutionModeWithoutTypeSchemaContext = specificActionSchemaContext.flatMap(_.updateByName("executionMode"))
  private lazy val copyActionExecutionModeWithTypeSchemaContext = copyActionExecutionModeWithoutTypeSchemaContext.flatMap(_.updateByType("DataFrameIncrementalMode"))
  private lazy val copyActionTransformerSchemaContext = specificActionSchemaContext.flatMap(_.updateByName("transformer"))
  private lazy val copyActionTransformersSchemaContext = specificActionSchemaContext.flatMap(_.updateByName("transformers"))
  private lazy val unknownActionTransformersSchemaContext = unknownActionSchemaContext.flatMap(_.updateByName("transformers"))

  // Level 4
  private lazy val copyActionTransformersAt1SchemaContext = copyActionTransformersSchemaContext.flatMap(_.updateByType("SQLDfTransformer"))


  "Schema Context" should "be updated with actions" in {
    val actionContext = actionSchemaContext
    actionContext shouldBe defined
    val localSchema = actionContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should contain key "type"
    localSchema.obj should contain key "additionalProperties"
    localSchema.obj should have size 2
  }

  it should "be updated with type=CopyAction given" in {
    val specificActionContext = specificActionSchemaContext
    specificActionContext shouldBe defined
    val localSchema = specificActionContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should contain key "type"
    localSchema.obj should contain key "properties"
    localSchema.obj should contain key "title"
    localSchema.obj should contain ("title" -> Str("CopyAction"))
  }

  it should "remain calm if type of action is still unknown" in {
    val unknownActionContext = unknownActionSchemaContext
    unknownActionContext shouldBe None
  }

  it should "be updated within metaData in CopyAction" in {
    val copyActionMetaDataContext = copyActionMetaDataSchemaContext
    copyActionMetaDataContext shouldBe defined
    val localSchema = copyActionMetaDataContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should have size 5
    localSchema.obj should contain key "type"
    localSchema.obj should contain key "properties"
    localSchema.obj should contain ("title" -> Str("ActionMetadata"))
  }

  it should "be updated within inputId in CopyAction" in {
    val copyActionInputIdContext = copyActionInputIdSchemaContext
    copyActionInputIdContext shouldBe defined
    val localSchema = copyActionInputIdContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should have size 2
    localSchema.obj should contain ("type" -> Str("string"))
    localSchema.obj should contain ("description" -> Str("inputs DataObject"))
  }

  it should "be updated within inputIds in CustomDataFrameAction" in {
    val copyActionInputIdsContext = actionSchemaContext
      .flatMap(_.updateByType("CustomDataFrameAction"))
      .flatMap(_.updateByName("inputIds"))
    copyActionInputIdsContext shouldBe defined
    val localSchema = copyActionInputIdsContext.get.localSchema
    localSchema shouldBe a[Obj]
    localSchema.obj should have size 3
    localSchema.obj should contain ("type" -> Str("array"))
    localSchema.obj should contain ("items" -> Obj("type" -> Str("string")))
  }

  it should "be updated within executionMode without provided type in CopyAction" in {
    val copyActionExecutionModeWithoutTypeContext = copyActionExecutionModeWithoutTypeSchemaContext
    copyActionExecutionModeWithoutTypeContext shouldBe defined
    val localSchema = copyActionExecutionModeWithoutTypeContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should have size 2
    localSchema.obj should contain ("description" -> Str("optional execution mode for this Action"))
    localSchema.obj should contain key "oneOf"
  }

  it should "be updated within executionMode with provided type=DataFrameIncrementalMode in CopyAction" in {
    val copyActionExecutionModeWithTypeContext = copyActionExecutionModeWithTypeSchemaContext
    copyActionExecutionModeWithTypeContext shouldBe defined
    val localSchema = copyActionExecutionModeWithTypeContext.get.localSchema
    localSchema shouldBe a[Obj]
    localSchema.obj should have size 6
    localSchema.obj should contain("title" -> Str("DataFrameIncrementalMode"))
    localSchema.obj should contain key "properties"
  }


  it should "be updated within transformer in CopyAction" in {
    val copyActionTransformerContext = copyActionTransformerSchemaContext
    copyActionTransformerContext shouldBe defined
    val localSchema = copyActionTransformerContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should have size 5
    localSchema.obj should contain ("title" -> Str("CustomDfTransformerConfig"))
  }

  it should "be updated within transformers in CopyAction" in {
    val copyActionTransformersContext = copyActionTransformersSchemaContext
    copyActionTransformersContext shouldBe defined
    val localSchema = copyActionTransformersContext.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should have size 3
    localSchema.obj should contain key "description"
    localSchema.obj should contain key "type"
    localSchema.obj should contain key "items"
  }

  it should "remain calm if trying to go further in path while still not knowing type of action" in {
    val unknownActionTransformersContext = unknownActionTransformersSchemaContext
    unknownActionTransformersContext shouldBe None
  }

  it should "be updated within second element of transformers list in CopyAction" in {
    val copyActionTransformersAt1Context = copyActionTransformersAt1SchemaContext
    copyActionTransformersAt1Context shouldBe defined
    val localSchema = copyActionTransformersAt1Context.get.localSchema
    localSchema shouldBe a [Obj]
    localSchema.obj should have size 6
    localSchema.obj should contain ("title" -> Str("SQLDfTransformer"))
    localSchema.obj should contain key "properties"
  }

  // ===================================================================================================================

  it should "generate template suggestions at actions level" in {
    val actionContext = actionSchemaContext
    actionContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate properties suggestion in specific action level" in {
    val specificActionContext = specificActionSchemaContext
    specificActionContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate suggestions within metaData in CopyAction level" in {
    val copyActionMetaDataContext = copyActionMetaDataSchemaContext
    copyActionMetaDataContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate nothing by itself for inputId as it is not schema-related suggestions" in {
    val copyActionInputIdContext = copyActionInputIdSchemaContext
    copyActionInputIdContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate nothing by itself for array inputIds as it is not schema-related suggestions" in {
    val copyActionInputIdsContext = actionSchemaContext
      .flatMap(_.updateByType("CustomDataFrameAction"))
      .flatMap(_.updateByName("inputIds"))
    copyActionInputIdsContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate template suggestions for executionMode" in { //TODO not ready yet
    val copyActionExecutionModeWithoutTypeContext = copyActionExecutionModeWithoutTypeSchemaContext
    copyActionExecutionModeWithoutTypeContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate suggestions within executionMode with provided type=DataFrameIncrementalMode in CopyAction" in {
    val copyActionExecutionModeWithTypeContext = copyActionExecutionModeWithTypeSchemaContext
    copyActionExecutionModeWithTypeContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate suggestions within transformer in CopyAction" in {
    val copyActionTransformerContext = copyActionTransformerSchemaContext
    copyActionTransformerContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  it should "generate template suggestions within transformers in CopyAction" in {
    val copyActionTransformersContext = copyActionTransformersSchemaContext
    copyActionTransformersContext.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  //TODO do "remain calm" tests for generations

  it should "generate suggestions within second element of transformers list in CopyAction" in {
    val copyActionTransformersAt1Context = copyActionTransformersAt1SchemaContext
    copyActionTransformersAt1Context.map(_.generateSchemaSuggestions).foreach(printSuggestions)
  }

  def printSuggestions(suggestions: AttributeCollection | TemplateCollection): Unit = suggestions match
    case AttributeCollection(attributes) => println(attributes.mkString("\n"))
    case TemplateCollection(templates) => println(templates.mkString("\n"))

}
