package io.smartdatalake.completion.ai.model

import io.smartdatalake.conversions.ScalaJavaConverterAPI.*

import sttp.client3.*
import sttp.client3.circe.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import io.smartdatalake.logging.SDLBLogger
import scala.concurrent.duration.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

// Gemini API data models
private object GeminiModels:
  // Request models
  case class GeminiRequest(
    contents: List[Content],
    generationConfig: Option[GenerationConfig] = Some(GenerationConfig(responseMimeType = Some("application/json")))
  )
  case class GenerationConfig(responseMimeType: Option[String])
  case class Content(parts: List[Part], role: Option[String] = None)
  case class Part(text: String)

  // Response models
  case class GeminiResponse(
    candidates: List[Candidate],
    usageMetadata: Option[UsageMetadata] = None
  )
  case class Candidate(
    content: Content, 
    finishReason: Option[String] = None,
    citationMetadata: Option[CitationMetadata] = None,
    avgLogprobs: Option[Double] = None
  )
  case class UsageMetadata(
    promptTokenCount: Int,
    candidatesTokenCount: Int,
    totalTokenCount: Int
  )
  case class CitationMetadata(citationSources: List[CitationSource])
  case class CitationSource(startIndex: Int, endIndex: Int, uri: String)

class GeminiClient(apiKey: Option[String], defaultModel: String = "gemini-2.0-flash-lite")(using ExecutionContext) extends ModelClient with SDLBLogger:
  import GeminiModels.*
  
  private val backend = HttpClientFutureBackend()
  private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"
  trace(s"Gemini API Key: ${if apiKey.isDefined then "correctly set" else "not set"}")

  def isEnabled: Boolean = apiKey.isDefined
  
  def completeAsync(prompt: String, model: String = defaultModel): Future[String] =
    if !isEnabled then
      warn("Gemini API key is not set. Skipping request.")
      Future.successful("")
    else
      val request = GeminiRequest(
        contents = List(Content(parts = List(Part(text = prompt)))),
        generationConfig = Some(GenerationConfig(responseMimeType = Some("application/json")))
      )
      val startInferenceTime = System.currentTimeMillis()
      basicRequest
        .post(uri"$baseUrl/$model:generateContent?key=${apiKey.getOrElse("")}")
        .header("Content-Type", "application/json")
        .body(request)
        .response(asJson[GeminiResponse])
        .readTimeout(3.seconds)
        .send(backend)
        .map { response =>
          response.body match
            case Right(geminiResponse) => 
              debug(s"Gemini response time: ${System.currentTimeMillis() - startInferenceTime} ms")
              geminiResponse.candidates.headOption
                .flatMap(candidate => candidate.content.parts.headOption)
                .map(_.text)
                .getOrElse("")
            case Left(error) =>
              warn(s"API error: ${error.toString}")
              ""
        }.recover {
          case e: Exception =>
            warn(s"Request failed: ${e.getMessage}")
            ""
        }
