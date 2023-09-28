package io.smartdatalake.context

import com.typesafe.config.ConfigObject
import org.eclipse.lsp4j.CompletionItem
import java.util.Map as JMap

import scala.collection.immutable.List

import io.smartdatalake.conversions.ScalaJavaConverterAPI.*


class ContextAdvisorImpl extends ContextAdvisor:
  override def generateSuggestions(context: SDLBContext): List[ContextSuggestion] = context.parentPath.lastOption match
    case Some(value) => value match
      case "inputId" | "outputId" | "inputIds" | "outputIds" => retrieveDataObjectIds(context)
      case _ => List.empty[ContextSuggestion]
    case None => List.empty[ContextSuggestion]

  private def retrieveDataObjectIds(context: SDLBContext): List[ContextSuggestion] =
    Option(context.textContext.rootConfig.root().get("dataObjects")) match
      case Some(asConfigObject: ConfigObject) => asConfigObject.unwrapped().toScala.map { (k, v) => v match
        case jMap: JMap[String, Object] => ContextSuggestion(k, Option(jMap.get("type")).map(_.toString).getOrElse(""))
        case _ => ContextSuggestion(k, "")
      }.toList
      case _ => List.empty[ContextSuggestion]

