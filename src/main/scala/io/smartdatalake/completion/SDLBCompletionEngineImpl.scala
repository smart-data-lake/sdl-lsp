package io.smartdatalake.completion

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.completion.SDLBCompletionEngine
import io.smartdatalake.context.{ContextAdvisor, ContextSuggestion, SDLBContext, TextContext}
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReader, SchemaReaderImpl, TemplateType}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind}

import scala.util.{Failure, Success, Try}

class SDLBCompletionEngineImpl(private val schemaReader: SchemaReader, private val contextAdvisor: ContextAdvisor) extends SDLBCompletionEngine {
  
  override def generateCompletionItems(context: SDLBContext): List[CompletionItem] =
    val itemSuggestionsFromSchema = schemaReader.retrieveAttributeOrTemplateCollection(context) match
      case AttributeCollection(attributes) => generateAttributeSuggestions(attributes, context.getParentContext)
      case TemplateCollection(templates, templateType) => generateTemplateSuggestions(templates, templateType, context.parentPath.size)

    val itemSuggestionsFromConfigContextSuggestions = contextAdvisor.generateSuggestions(context)
    val longestItemLength = itemSuggestionsFromConfigContextSuggestions.maxByOption(_.value.length).map(_.value.length).getOrElse(0)
    val itemSuggestionsFromConfig = itemSuggestionsFromConfigContextSuggestions.map(createCompletionItem(_, longestItemLength))
    val allItems = itemSuggestionsFromConfig ++ itemSuggestionsFromSchema
    if allItems.isEmpty then typeList else allItems //TODO too aggressive. Sometimes suggesting nothing is better
    
  private def generateAttributeSuggestions(attributes: Iterable[SchemaItem], parentContext: Option[ConfigValue]): List[CompletionItem] =
    val items = parentContext match
      case Some(config: ConfigObject) => attributes.filter(item => Option(config.get(item.name)).isEmpty)
      case _ => attributes
    val longestItemLength = items.maxByOption(_.name.length).map(_.name.length).getOrElse(0)
    items.map(createCompletionItem(_, longestItemLength)).toList

  private[completion] def generateTemplateSuggestions(templates: Iterable[(String, Iterable[SchemaItem])], templateType: TemplateType, depth: Int): List[CompletionItem] =
    val indentDepth = depth + (if templateType == TemplateType.ATTRIBUTES then 0 else 1)
    templates.map { case (actionType, attributes) =>
      val completionItem = new CompletionItem()
      completionItem.setLabel(actionType.toLowerCase)
      completionItem.setDetail("  template")
      val keyName = if templateType == TemplateType.OBJECT then s"${actionType.toLowerCase}_PLACEHOLDER" else ""
      val startObject = if templateType != TemplateType.ATTRIBUTES then "{" else ""
      val endObject = if templateType != TemplateType.ATTRIBUTES then ("  " * (indentDepth-1)) + "}" else ""
      completionItem.setInsertText(
        s"""$keyName $startObject
          |${
          def generatePlaceHolderValue(att: SchemaItem) = {
            if att.name == "type" then actionType else att.itemType.defaultValue
          }
          attributes.map(att => ("  " * indentDepth) + att.name + " = " + generatePlaceHolderValue(att)).mkString("\n")}\n$endObject
          |""".stripMargin.replace("\r\n", "\n").trim)
      completionItem.setKind(CompletionItemKind.Snippet)
      completionItem
    }.toList

  private def createCompletionItem(item: SchemaItem, longestItemLength: Int): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.name)
    val trailingSpaces = " " * (2 + longestItemLength - item.name.length)
    completionItem.setDetail(trailingSpaces + s"${if item.required then "required" else ""} ${item.itemType.name}")
    completionItem.setInsertText(item.name + (if item.itemType == ItemType.OBJECT then " " else " = ") + item.itemType.defaultValue)
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem

  private def createCompletionItem(item: ContextSuggestion, longestItemLength: Int): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.value)
    val trailingSpaces = " " * (2 + longestItemLength - item.value.length)
    completionItem.setDetail(trailingSpaces + s"${item.label}")
    completionItem.setInsertText(item.value)
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem

  private val typeItem = createCompletionItem(SchemaItem("type", ItemType.STRING, " type of object", true), 4)
  private val typeList = List(typeItem)
}
