package io.smartdatalake.hover
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.{SchemaReader, SchemaReaderImpl}
import org.eclipse.lsp4j.{Hover, MarkupContent, MarkupKind}

import scala.util.{Failure, Success, Try}

class SDLBHoverEngineImpl(private val schemaReader: SchemaReader) extends SDLBHoverEngine:

  override def generateHoveringInformation(context: SDLBContext): Hover =
    val markupContent = new MarkupContent()
    markupContent.setKind(MarkupKind.MARKDOWN)
    markupContent.setValue(retrieveSchemaDescription(context))
    new Hover(markupContent)

  private def retrieveSchemaDescription(context: SDLBContext): String =
    context.parentPath match
      case path if path.startsWith("actions") && path.count(_ == '.') == 1 =>
        val tActionType: Try[String] = Try(context.textContext.config.getString(context.parentPath + ".type"))
        tActionType match
          case Success(actionType) => schemaReader.retrieveActionPropertyDescription(actionType, context.word)
          case Failure(_) => ""

      case _ => ""

