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
      case TemplateCollection(templates, templateType) => generateTemplateSuggestions(templates, templateType)

    val itemSuggestionsFromConfig = contextAdvisor.generateSuggestions(context).map(createCompletionItem)
    val allItems = itemSuggestionsFromConfig ++ itemSuggestionsFromSchema
    if allItems.isEmpty then typeList else allItems //TODO wrong
    
  private def generateAttributeSuggestions(attributes: Iterable[SchemaItem], parentContext: Option[ConfigValue]): List[CompletionItem] =
    val items = parentContext match
      case Some(config: ConfigObject) => attributes.filter(item => Option(config.get(item.name)).isEmpty)
      case _ => attributes
    items.map(createCompletionItem).toList

  private[completion] def generateTemplateSuggestions(templates: Iterable[(String, Iterable[SchemaItem])], templateType: TemplateType): List[CompletionItem] =
    templates.map { case (actionType, attributes) =>
      val completionItem = new CompletionItem()
      completionItem.setLabel(actionType.toLowerCase)
      completionItem.setDetail("  template")
      val keyName = if templateType == TemplateType.OBJECT then s"${actionType.toLowerCase}_PLACEHOLDER" else ""
      val startObject = if templateType != TemplateType.ATTRIBUTES then "{" else ""
      val endObject = if templateType != TemplateType.ATTRIBUTES then "}" else ""
      completionItem.setInsertText( //TODO handle indentation
        s"""$keyName $startObject
          |${
          def generatePlaceHolderValue(att: SchemaItem) = {
            if att.name == "type" then actionType else att.itemType.defaultValue
          }
          attributes.map(att => "\t\t" + att.name + " = " + generatePlaceHolderValue(att)).mkString("\n")}\n\t$endObject
          |""".stripMargin.replace("\r\n", "\n")) //TODO remove blank lines?
      completionItem.setKind(CompletionItemKind.Snippet)
      completionItem
    }.toList

  private def createCompletionItem(item: SchemaItem): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.name)
    completionItem.setDetail(f"  ${if item.required then "required" else ""}%s ${item.itemType.name}%-10s") //TODO check how to justify properly
    completionItem.setInsertText(item.name + (if item.itemType == ItemType.OBJECT then " " else " = ") + item.itemType.defaultValue)
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem

  private def createCompletionItem(item: ContextSuggestion): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.value)
    completionItem.setDetail(s"   ${item.label}")
    completionItem.setInsertText(item.value)
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem

  private val typeItem = createCompletionItem(SchemaItem("type", ItemType.STRING, " type of object", true))
  private val typeList = List(typeItem)
}
