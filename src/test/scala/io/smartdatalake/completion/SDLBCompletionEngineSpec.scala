package io.smartdatalake.completion

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext


class SDLBCompletionEngineSpec extends UnitSpec {
  
  private val context = SDLBContext.fromText(loadFile("fixture/hocon/with-lists-example.conf"))
  
  "SDLB Completion engine" should "retrieve all the properties of copyAction" in {
    val context = SDLBContext.fromText(loadFile("fixture/hocon/with-multi-lines-flattened-example.conf"))
      .withCaretPosition(16, 0)
    completionEngine.generateCompletionItems(context) should have size 12
  }

  it should "generate completion items inside a specific action" in {
    completionEngine.generateCompletionItems(context.withCaretPosition(3, 0)) should have size 9
  }

  it should "generate completion items within a specific transformer" in {
    completionEngine.generateCompletionItems(context.withCaretPosition(7, 0)) should have size 4
  }

  it should "give the description of the attributes as documentation" in {
    val documentations = completionEngine.generateCompletionItems(context.withCaretPosition(3, 0))
      .flatMap(item => Option(item.getDocumentation)).map(_.getRight)
    documentations should not be empty
    all(documentations.map(_.getKind)) shouldBe "markdown"
    all(documentations.map(_.getValue.isBlank)) shouldBe false
  }

  it should "give the description of the types as documentation of their values, but not of templates" in {
    val typeValue = completionEngine.generateCompletionItems(context.withCaretPosition(7, 999))
      .find(_.getLabel == "SQLDfTransformer").get
    typeValue.getDocumentation.getRight.getValue should startWith ("Configuration of a custom GenericDataFrame transformation")

    val template = completionEngine.generateCompletionItems(SDLBContext.fromText("actions {\n\n}").withCaretPosition(2, 0))
      .find(_.getLabel == "copyaction").get
    template.getDocumentation shouldBe null
  }
}
