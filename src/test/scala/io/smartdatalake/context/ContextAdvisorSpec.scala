package io.smartdatalake.context

import io.smartdatalake.UnitSpec


class ContextAdvisorSpec extends UnitSpec {

  private val context: SDLBContext = SDLBContext.fromText(loadFile("fixture/hocon/airport-example.conf"))


  "Context Advisor" should "generate correctly dataObject suggestions when on inputId attribute" in {
    val inputIdContext = context.withCaretPosition(62, 14)
    val suggestions = contextAdvisor.generateSuggestions(inputIdContext)
    suggestions should have size 7
    suggestions should contain (ContextSuggestion("ext-airports", "WebserviceFileDataObject"))
    suggestions should contain (ContextSuggestion("btl-distances", "CsvFileDataObject"))

  }

  it should "suggest connection ids when on connectionId attribute" in {
    val text = "connections {\n  my-db { type = JdbcTableConnection }\n}\ndataObjects {\n  obj {\n    connectionId = \n  }\n}"
    val suggestions = contextAdvisor.generateSuggestions(SDLBContext.fromText("connections {\n  my-db { type = JdbcTableConnection }\n}").withText(text).withCaretPosition(6, 19))
    suggestions shouldBe List(ContextSuggestion("my-db", "JdbcTableConnection"))
  }

  it should "clean ids under the caret" in {
    References.cleanId("\"my-obj\",") shouldBe "my-obj"
    References.cleanId("[my-obj") shouldBe "my-obj"
    References.cleanId("my-obj]") shouldBe "my-obj"
  }

}
