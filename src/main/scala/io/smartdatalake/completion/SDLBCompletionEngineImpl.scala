package io.smartdatalake.completion

import io.smartdatalake.completion.SDLBCompletionEngine
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReader, SchemaReaderImpl}
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind}

import scala.util.{Failure, Success, Try}

class SDLBCompletionEngineImpl(private val schemaReader: SchemaReader) extends SDLBCompletionEngine {

  //val schemaReader: SchemaReader = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json") //TODO should be retrieved from a service keeping its state, object for example

  override def generateCompletionItems(context: SDLBContext): List[CompletionItem] = context.parentPath match
    case path if path.startsWith("actions") && path.count(_ == '.') == 1 => generatePropertiesOfAction(context)
    case "actions"  => generateTemplatesForAction()
    case path if path.startsWith("actions") => List.empty[CompletionItem] //TODO when going deeper find a good recursive approach and mb merge it with first case
    case _ => List.empty[CompletionItem]


  private[completion] def generateTemplatesForAction(): List[CompletionItem] =
    val actionsWithRequiredAttr = schemaReader.retrieveActionTypesWithRequiredAttributes()
    actionsWithRequiredAttr.map { case (actionType, attributes) =>
      val completionItem = new CompletionItem()
      completionItem.setLabel(actionType.toLowerCase)
      completionItem.setDetail("  template")
      completionItem.setInsertText(
        s"""${actionType.toLowerCase}_PLACEHOLDER {
          |${
          def generatePlaceHolderValue(att: SchemaItem) = {
            if att.name == "type" then actionType else att.itemType.defaultValue
          }
          attributes.map(att => "\t\t" + att.name + " = " + generatePlaceHolderValue(att)).mkString("\n")}\n\t}
          |""".stripMargin)
      completionItem.setKind(CompletionItemKind.Snippet)
      completionItem
    }.toList


  private def generatePropertiesOfAction(context: SDLBContext): List[CompletionItem] =
    def isMissingInConfig(item: SchemaItem): Boolean = Try(context.textContext.config.getAnyRef(context.parentPath + "." + item.name)).isFailure
    val tActionType: Try[String] = Try(context.textContext.config.getString(context.parentPath + ".type"))
    tActionType match
      case Success(actionType) => schemaReader.retrieveActionProperties(actionType).filter(isMissingInConfig).map(createCompletionItem).toList
      case Failure(_) => typeList

  private def createCompletionItem(item: SchemaItem): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.name)
    completionItem.setDetail(f"  ${if item.required then "required" else ""}%s ${item.itemType.name}%-10s") //TODO check how to justify properly
    completionItem.setInsertText(item.name + (if item.itemType.isComplexValue then " " else " = "))
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem

  private val typeItem = createCompletionItem(SchemaItem("type", ItemType.STRING, " type of object", true))
  private val typeList = List(typeItem)
}
