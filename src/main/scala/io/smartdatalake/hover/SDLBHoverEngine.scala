package io.smartdatalake.hover

import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.Hover

trait SDLBHoverEngine:
  def generateHoveringInformation(context: SDLBContext): Hover
