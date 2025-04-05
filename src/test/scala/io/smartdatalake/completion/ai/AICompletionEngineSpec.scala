package io.smartdatalake.completion.ai

import io.smartdatalake.UnitSpec
import io.smartdatalake.completion.ai.model.GeminiClient

class AICompletionEngineSpec extends UnitSpec {

    "AI Completion engine" should "have LLM Client disabled without API Token" in {
        assert(!aiCompletionEngine.isEnabled)
    }

    it should "be enabled if API Token found" in {
        val newAiCompletionEngine = new AICompletionEngineImpl(new GeminiClient(Some("fake-api-key-token-for-testing")))
        assert(newAiCompletionEngine.isEnabled)
    }

    it should "correctly replace tabstops" in {
        val insertText = """${1:copyaction_PLACEHOLDER} {
        |  type = CopyAction
        |  inputId = ${3:inputId}
        |  outputId = ${4:outputId}${0}
        |}""".stripMargin

        val jsonResponse = """[
        |  {
        |    "tab_stop_number": 1,
        |    "new_value": "copyaction_departures_to_ext"
        |  },
        |  {
        |    "tab_stop_number": 3,
        |    "new_value": "ext-departures"
        |  },
        |  {
        |    "tab_stop_number": 4,
        |    "new_value": "stg-departures"
        |  }
        |]""".stripMargin

        val expectedResult = """${1:copyaction_departures_to_ext} {
        |  type = CopyAction
        |  inputId = ${3:ext-departures}
        |  outputId = ${4:stg-departures}${0}
        |}""".stripMargin
        val result = aiCompletionEngine.applyTabStopReplacements(insertText, jsonResponse)
        assert(result == expectedResult)
    }
}
