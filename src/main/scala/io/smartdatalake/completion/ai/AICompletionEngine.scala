package io.smartdatalake.completion.ai

import io.smartdatalake.conversions.ScalaJavaConverterAPI.*

import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*

import io.smartdatalake.logging.SDLBLogger
import scala.util.Try
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import io.smartdatalake.completion.ai.model.ModelClient

trait AICompletionEngine:
  def isEnabled: Boolean
  def generateInsertTextWithTabStops(insertText: String, parentpath: String, context: String): Future[String]

class AICompletionEngineImpl(modelClient: ModelClient)(using ExecutionContext) extends AICompletionEngine with SDLBLogger:

  private case class TabStopReplacement(tab_stop_number: Int, new_value: String)
  export modelClient.isEnabled

  def generateInsertTextWithTabStops(insertText: String, parentpath: String, context: String): Future[String] =
    val promptText = 
      """You're helping a user with code completion in an IDE.
         |The user's current file is about a Smart Data Lake Builder configuration, where the "dataObjects" block provides all the data sources
         |and the "actions" block usually defines a transformation from one or more data sources to another.
         |Extract a list of suggested tab stops default values.
         |Tab stops have the following format in the default insert text: ${number:default_value}.
         |Use the context text to suggest better default values.
         |Concerning the title of the object, try to infer the intention of the user.
         |For example, copying from the web to a json could be interpreted as a download action.
         |Output should be valid JSON with this schema:
         |[
         |  {
         |    "tab_stop_number": number,
         |    "new_value": string
         |  }
         |]
         |
         |Default insert text:
         |$insertText
         |
         |Suggested item is to be inserted in the following HOCON path:
         |$parentPath
         |
         |Context text, HOCON format, the user's current file:
         |$contextText""".stripMargin
                         .replace("$insertText", insertText)
                         .replace("$parentPath", parentpath)
                         .replace("$contextText", context)
                         .take(80_000)
    
    trace("calling Gemini client asynchronously...")
    val jsonResponse: Future[String] = modelClient.completeAsync(promptText)
    jsonResponse.map { jsonResponse =>
      applyTabStopReplacements(insertText, jsonResponse)
    }.recover {
      case e: Exception =>
        trace(s"Error generating insert text with tab stops: ${e.getMessage}")
        insertText
    }
    
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
      val replacementsOpt = parse(jsonResponse).flatMap(_.as[List[TabStopReplacement]]).toOption
      replacementsOpt.map(processReplacements).getOrElse(insertText)
    }.recover {
      case e: Exception =>
        trace(s"Error applying tab stop replacements: ${e.getMessage}")
        insertText
    }.get