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

}
