package io.smartdatalake.completion

import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.CompletionItem

trait SDLBCompletionEngine:
  def generateCompletionItems(context: SDLBContext): List[CompletionItem]
