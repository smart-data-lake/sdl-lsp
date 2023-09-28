package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigRenderOptions, ConfigUtil}
import io.smartdatalake.UnitSpec
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer as MLT

import scala.io.Source
import scala.util.Using

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
