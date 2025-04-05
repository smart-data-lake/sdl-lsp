package io.smartdatalake.completion

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.completion.SDLBCompletionEngine
import io.smartdatalake.context.{ContextAdvisor, ContextSuggestion, SDLBContext, TextContext}
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReader, SchemaReaderImpl, TemplateType}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind, InsertTextFormat}

import scala.util.{Failure, Success, Try}
import io.smartdatalake.logging.SDLBLogger

class SDLBCompletionEngineImpl(private val schemaReader: SchemaReader, private val contextAdvisor: ContextAdvisor)
  extends SDLBCompletionEngine with SDLBLogger {
  
  override def generateCompletionItems(context: SDLBContext): List[CompletionItem] =
    val itemSuggestionsFromSchema = schemaReader.retrieveAttributeOrTemplateCollection(context) match
      case AttributeCollection(attributes) => generateAttributeSuggestions(attributes, context.getParentContext)
      case TemplateCollection(templates, templateType) => generateTemplateSuggestions(templates, templateType, context)

    val itemSuggestionsFromConfigContextSuggestions = contextAdvisor.generateSuggestions(context)
    val itemSuggestionsFromConfig = itemSuggestionsFromConfigContextSuggestions.map(createCompletionItem)
    val allItems = itemSuggestionsFromConfig ++ itemSuggestionsFromSchema
    if allItems.isEmpty then typeList else allItems //TODO too aggressive. Sometimes suggesting nothing is better
    
  private def generateAttributeSuggestions(attributes: Iterable[SchemaItem], parentContext: Option[ConfigValue]): List[CompletionItem] =
    val items = parentContext match
      case Some(config: ConfigObject) => attributes.filter(item => Option(config.get(item.name)).isEmpty)
      case _ => attributes
    items.map(createCompletionItem).toList

  private[completion] def generateTemplateSuggestions(templates: Iterable[(String, Iterable[SchemaItem])], templateType: TemplateType, context: SDLBContext): List[CompletionItem] =
    templates.map { case (actionType, attributes) =>
      val completionItem = new CompletionItem()
      completionItem.setLabel(actionType.toLowerCase)
      completionItem.setDetail("template")
      
      val keyName = if templateType == TemplateType.OBJECT then s"$${1:${actionType.toLowerCase}_PLACEHOLDER}" else ""
      val startObject = if templateType != TemplateType.ATTRIBUTES then "{" else ""
      val endObject = if templateType != TemplateType.ATTRIBUTES then "}" else ""
      
      // Build attribute snippets with tabstops
      val attributeSnippets = attributes.zipWithIndex.map { case (att, idx) =>
        val defaultValue = 
          if att.name == "type" then actionType 
          else s"$${${idx + 2}:${att.name}}"
        
        "  " + att.name + " = " + defaultValue
      }.mkString("\n")
      
      completionItem.setInsertText(
        s"""$keyName $startObject
          |$attributeSnippets$${0}
          |$endObject
          |""".stripMargin.replace("\r\n", "\n").trim)
      
      completionItem.setKind(CompletionItemKind.Snippet)
      completionItem.setInsertTextFormat(InsertTextFormat.Snippet)
      if completionItem.getInsertText.contains("${1:") then
        val data = CompletionData(
          withTabStops = true,
          parentPath = context.parentPath.mkString("->"),
          context = context.textContext.rootConfig.root().toString
        )
        completionItem.setData(data.toJson)
      completionItem
    }.toList

  private def createCompletionItem(item: SchemaItem): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.name)
    completionItem.setDetail(s"${if item.required then "required" else ""} ${item.itemType.name}".trim())
    
    val valuePart = 
      if Set(ItemType.OBJECT, ItemType.TYPE_VALUE).contains(item.itemType) then 
        s" ${item.itemType.defaultValue}" 
      else 
        s" = ${item.itemType.defaultValue}"
    
    completionItem.setInsertText(item.name + valuePart)
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem.setInsertTextFormat(InsertTextFormat.Snippet)
    completionItem

  private def createCompletionItem(item: ContextSuggestion): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.value)
    completionItem.setDetail(item.label)
    completionItem.setInsertText(item.value)
    completionItem.setKind(CompletionItemKind.Variable)
    completionItem

  private val typeItem = createCompletionItem(SchemaItem("type", ItemType.STRING, " type of object", true))
  private val typeList = List(typeItem)
}
