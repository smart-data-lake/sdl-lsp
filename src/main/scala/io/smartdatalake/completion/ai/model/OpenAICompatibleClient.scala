package io.smartdatalake.completion.ai.model

import sttp.client3.*
import sttp.client3.circe.*
import sttp.model.Uri
import io.circe.generic.auto.*
import io.smartdatalake.logging.SDLBLogger
import scala.concurrent.duration.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

// OpenAI chat completions API data models
private object ChatCompletionModels:
  case class ChatRequest(model: String, messages: List[ChatMessage])
  case class ChatMessage(role: String, content: String)

  case class ChatResponse(choices: List[Choice])
  case class Choice(message: ResponseMessage)
  case class ResponseMessage(content: Option[String])

/**
  * Client for any endpoint implementing the OpenAI chat completions API:
  * OpenAI, Gemini (default, through its OpenAI compatibility layer), local servers like Ollama, ...
  *
  * @param apiKey sent as bearer token. Without API key, the client is only enabled if a base URL is configured explicitly.
  */
class OpenAICompatibleClient(apiKey: Option[String])(using ExecutionContext) extends ModelClient with SDLBLogger:
  import ChatCompletionModels.*
  import OpenAICompatibleClient.*

  private val backend = HttpClientFutureBackend()
  @volatile private var configuredBaseUrl: Option[String] = None
  trace(s"AI API Key: ${if apiKey.isDefined then "correctly set" else "not set"}")

  def baseUrl: String = configuredBaseUrl.getOrElse(DEFAULT_BASE_URL)

  def configure(baseUrl: Option[String]): Unit =
    configuredBaseUrl = baseUrl.map(_.trim).filter(_.nonEmpty)

  def isEnabled: Boolean = apiKey.isDefined || configuredBaseUrl.isDefined

  def completeAsync(prompt: String, model: Option[String] = None): Future[String] =
    val modelName = model.getOrElse(DEFAULT_MODEL)
    if !isEnabled then
      warn("AI API key is not set. Skipping request.")
      Future.successful("")
    else Uri.parse(baseUrl.stripSuffix("/") + "/chat/completions") match
      case Left(error) =>
        warn(s"Invalid AI base URL $baseUrl: $error")
        Future.successful("")
      case Right(endpoint) =>
        val startInferenceTime = System.currentTimeMillis()
        val request = basicRequest
          .post(endpoint)
          .header("Content-Type", "application/json")
          .body(ChatRequest(modelName, List(ChatMessage("user", prompt))))
          .response(asJson[ChatResponse])
          .readTimeout(3.seconds)
        apiKey.fold(request)(key => request.auth.bearer(key))
          .send(backend)
          .map { response =>
            response.body match
              case Right(chatResponse) =>
                debug(s"AI ($modelName) response time: ${System.currentTimeMillis() - startInferenceTime} ms")
                chatResponse.choices.headOption.flatMap(_.message.content).getOrElse("")
              case Left(error) =>
                warn(s"AI API error with model $modelName: ${error.toString}")
                ""
          }.recover {
            case e: Exception =>
              warn(s"AI request failed: ${e.getMessage}")
              ""
          }

object OpenAICompatibleClient:
  val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai"
  val DEFAULT_MODEL = "gemini-3.5-flash-lite"
  val API_KEY_ENV_VARIABLES: List[String] = List("AI_API_KEY", "GOOGLE_API_KEY")

  def apiKeyFromEnvironment: Option[String] =
    API_KEY_ENV_VARIABLES.flatMap(name => Option(System.getenv(name))).map(_.trim).find(_.nonEmpty)
