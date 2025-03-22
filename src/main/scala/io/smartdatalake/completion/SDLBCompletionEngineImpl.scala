package io.smartdatalake.completion

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.completion.SDLBCompletionEngine
import io.smartdatalake.context.{ContextAdvisor, ContextSuggestion, SDLBContext, TextContext}
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReader, SchemaReaderImpl, TemplateType}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind, InsertTextFormat, InsertTextMode}

import scala.util.{Failure, Success, Try}

class SDLBCompletionEngineImpl(private val schemaReader: SchemaReader, private val contextAdvisor: ContextAdvisor) extends SDLBCompletionEngine {
  
  override def generateCompletionItems(context: SDLBContext): List[CompletionItem] =
    val itemSuggestionsFromSchema = schemaReader.retrieveAttributeOrTemplateCollection(context) match
      case AttributeCollection(attributes) => generateAttributeSuggestions(attributes, context.getParentContext)
      case TemplateCollection(templates, templateType) => generateTemplateSuggestions(templates, templateType, context.parentPath.size)

    val itemSuggestionsFromConfigContextSuggestions = contextAdvisor.generateSuggestions(context)
    val itemSuggestionsFromConfig = itemSuggestionsFromConfigContextSuggestions.map(createCompletionItem)
    val allItems = itemSuggestionsFromConfig ++ itemSuggestionsFromSchema
    if allItems.isEmpty then typeList else allItems //TODO too aggressive. Sometimes suggesting nothing is better
    
  private def generateAttributeSuggestions(attributes: Iterable[SchemaItem], parentContext: Option[ConfigValue]): List[CompletionItem] =
    val items = parentContext match
      case Some(config: ConfigObject) => attributes.filter(item => Option(config.get(item.name)).isEmpty)
      case _ => attributes
    items.map(createCompletionItem).toList

  private[completion] def generateTemplateSuggestions(templates: Iterable[(String, Iterable[SchemaItem])], templateType: TemplateType, depth: Int): List[CompletionItem] =
    val indentDepth = depth + (if templateType == TemplateType.ATTRIBUTES then 0 else 1)
    templates.map { case (actionType, attributes) =>
      val completionItem = new CompletionItem()
      completionItem.setLabel(actionType.toLowerCase)
      completionItem.setDetail("template")
      
      val keyName = if templateType == TemplateType.OBJECT then s"$${1:${actionType.toLowerCase}_PLACEHOLDER}" else ""
      val startObject = if templateType != TemplateType.ATTRIBUTES then "{" else ""
      val endObject = if templateType != TemplateType.ATTRIBUTES then ("  " * (indentDepth-2)) + "}" else ""
      
      // Build attribute snippets with tabstops
      val attributeSnippets = attributes.zipWithIndex.map { case (att, idx) =>
        val defaultValue = 
          if att.name == "type" then actionType 
          else s"$${${idx + 2}:${att.name}}"
        
        ("  " * indentDepth) + att.name + " = " + defaultValue
      }.mkString("\n")
      
      completionItem.setInsertText(
        s"""$keyName $startObject
          |$attributeSnippets$${0}
          |$endObject
          |""".stripMargin.replace("\r\n", "\n").trim)
      
      completionItem.setKind(CompletionItemKind.Snippet)
      completionItem.setInsertTextFormat(InsertTextFormat.Snippet)
      completionItem.setInsertTextMode(InsertTextMode.AsIs)
      completionItem
    }.toList

  private def createCompletionItem(item: SchemaItem): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.name)
    completionItem.setDetail(s"${if item.required then "required" else ""} ${item.itemType.name}")
    
    val valuePart = 
      if Set(ItemType.OBJECT, ItemType.TYPE_VALUE).contains(item.itemType) then 
        " " 
      else 
        " = ${1:" + item.itemType.defaultValue + "}"
    
    completionItem.setInsertText(item.name + valuePart + "$0")
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
