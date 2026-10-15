package io.smartdatalake.completion.ai


import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*

import com.typesafe.config.ConfigFactory
import io.smartdatalake.logging.SDLBLogger
import scala.util.Try
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import io.smartdatalake.completion.ai.model.ModelClient

trait AICompletionEngine:
  def isEnabled: Boolean
  def generateInsertTextWithTabStops(insertText: String, parentpath: String, context: String): Future[String]

class AICompletionEngineImpl(modelClient: ModelClient)(using ExecutionContext) extends AICompletionEngine with SDLBLogger:

  private val maxInsertTextLength = 80_000
  // Single source of truth: the prompt of the LSP config template
  private lazy val defaultTabStopsPrompt: String =
    Try(ConfigFactory.parseResources(getClass.getClassLoader, "lsp-config/default-config.conf").getString("tabStopsPrompt"))
      .getOrElse {
        warn("Default tab stops prompt not found")
        ""
      }

  var tabStopsPrompt: Option[String] = None
  var model: Option[String] = None

  private case class TabStopReplacement(tab_stop_number: Int, new_value: String)
  export modelClient.isEnabled

  def generateInsertTextWithTabStops(insertText: String, parentpath: String, context: String): Future[String] =
    val promptText = tabStopsPrompt.getOrElse(defaultTabStopsPrompt)
                         .replace("$insertText", insertText)
                         .replace("$parentPath", parentpath)
                         .replace("$contextText", context)
                         .take(maxInsertTextLength)
    
    trace("calling model client asynchronously...")
    val jsonResponse: Future[String] = modelClient.completeAsync(promptText, model)
    jsonResponse.map { jsonResponse =>
      applyTabStopReplacements(insertText, jsonResponse)
    }.recover {
      case e: Exception =>
        trace(s"Error generating insert text with tab stops: ${e.getMessage}")
        insertText
    }
    
  /** Models may wrap the JSON in markdown code fences or add some text around it */
  private[ai] def extractJsonArray(response: String): String =
    val start = response.indexOf('[')
    val end = response.lastIndexOf(']')
    if start >= 0 && end > start then response.substring(start, end + 1) else response

  private[ai] def applyTabStopReplacements(insertText: String, jsonResponse: String): String =
    def processReplacements(replacements: List[TabStopReplacement]): String =
      // Sort by tab stop number in descending order to avoid offset issues when replacing
      val sortedReplacements = replacements.sortBy(-_.tab_stop_number)
      var result = insertText
      for replacement <- sortedReplacements do
        val tabStopToFind = "${" + replacement.tab_stop_number + ":"
        val closingBrace = "}"
        
        // Find the tab stop in the text
        val startIndex = result.indexOf(tabStopToFind)
        if startIndex >= 0 then
            // Find the closing brace
            val valueStartIndex = startIndex + tabStopToFind.length
            val valueEndIndex = result.indexOf(closingBrace, valueStartIndex)
            if valueEndIndex >= 0 then
              // Replace just the content between the colon and closing brace
              val before = result.substring(0, valueStartIndex)
              val after = result.substring(valueEndIndex)
              result = before + replacement.new_value + after 
      result

    Try {
      val replacementsOpt = parse(extractJsonArray(jsonResponse)).flatMap(_.as[List[TabStopReplacement]]).toOption
      replacementsOpt.map(processReplacements).getOrElse(insertText)
    }.recover {
      case e: Exception =>
        trace(s"Error applying tab stop replacements: ${e.getMessage}")
        insertText
    }.get