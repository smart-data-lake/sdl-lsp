package io.smartdatalake.completion

import io.smartdatalake.completion.SDLBCompletionEngine
import io.smartdatalake.completion.schema.{ItemType, SchemaItem, SchemaReader, SchemaReaderImpl}
import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind}

import scala.util.{Failure, Success, Try}

class SDLBCompletionEngineImpl extends SDLBCompletionEngine {

  val schemaReader: SchemaReader = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json") //TODO should be retrieved from a service keeping its state, object for example

  override def generateCompletionItems(context: SDLBContext): List[CompletionItem] = context.parentPath match
    case path if path.startsWith("actions") && path.count(_ == '.') == 1 => generatePropertiesOfAction(context)
    case path if path.startsWith("actions") && !path.contains('.')  => List.empty[CompletionItem] //TODO discuss about this placeholder idea
    case path if path.startsWith("actions") => List.empty[CompletionItem] //TODO when going deeper find a good recursive approach and mb merge it with first case
    case _ => List.empty[CompletionItem]


  private def generatePropertiesOfAction(context: SDLBContext): List[CompletionItem] =
    val tActionType: Try[String] = Try(context.config.getString(context.parentPath + ".type"))
    tActionType match
      case Success(actionType) => schemaReader.retrieveActionProperties(actionType).map(createCompletionItem).toList
      case Failure(_) => typeList

  private def createCompletionItem(item: SchemaItem): CompletionItem =
    val completionItem = new CompletionItem()
    completionItem.setLabel(item.name)
    completionItem.setDetail(item.description)
    completionItem.setInsertText(item.name + (if item.itemType.isComplexValue then " " else " = "))
    completionItem.setKind(CompletionItemKind.Snippet)
    completionItem

  private val typeItem = createCompletionItem(SchemaItem("type", ItemType.STRING, " type of object"))
  private val typeList = List(typeItem)
}
