package io.smartdatalake.completion.ai

import io.smartdatalake.UnitSpec
import io.smartdatalake.completion.ai.model.{ModelClient, OpenAICompatibleClient}

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}

class AICompletionEngineSpec extends UnitSpec {

    "AI Completion engine" should "have LLM Client disabled without API Token" in {
        assert(!aiCompletionEngine.isEnabled)
    }

    it should "be enabled if API Token found" in {
        val newAiCompletionEngine = new AICompletionEngineImpl(new OpenAICompatibleClient(Some("fake-api-key-token-for-testing")))
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

    it should "send the default prompt and the configured model to the model client" in {
        var received: Option[(String, Option[String])] = None
        val recordingClient = new ModelClient:
            override def isEnabled: Boolean = true
            override def completeAsync(prompt: String, model: Option[String]): Future[String] =
                received = Some((prompt, model))
                Future.successful("""[{"tab_stop_number": 1, "new_value": "my_action"}]""")
        val engine = new AICompletionEngineImpl(recordingClient)
        engine.model = Some("my-model")

        Await.result(engine.generateInsertTextWithTabStops("${1:name} {}", "actions", "context"), 5.seconds) shouldBe "${1:my_action} {}"
        val (prompt, model) = received.value
        model shouldBe Some("my-model")
        prompt should include ("Smart Data Lake Builder")
        prompt should include ("${1:name} {}")
        prompt should not include ("$insertText")
    }

    it should "extract the JSON array from replies wrapped in markdown" in {
        val reply = "Here you go:\n```json\n[{\"tab_stop_number\": 1, \"new_value\": \"my_action\"}]\n```"
        aiCompletionEngine.applyTabStopReplacements("${1:name} {}", reply) shouldBe "${1:my_action} {}"
    }
}
