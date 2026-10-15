package io.smartdatalake.hover
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaReader
import org.eclipse.lsp4j.{Hover, MarkupContent, MarkupKind}

class SDLBHoverEngineImpl(private val schemaReader: SchemaReader) extends SDLBHoverEngine:

  override def generateHoveringInformation(context: SDLBContext): Option[Hover] =
    Option(schemaReader.retrieveDescription(context)).filterNot(_.isBlank).map { description =>
      val markupContent = new MarkupContent()
      markupContent.setKind(MarkupKind.MARKDOWN)
      markupContent.setValue(description)
      new Hover(markupContent)
    }
