package io.smartdatalake.formatting

import org.eclipse.lsp4j.CompletionItem
import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.CompletionParams
import scala.annotation.unused

trait FormattingStrategy:
    val indentSize: Int = 2
    lazy val indent: String = " " * indentSize

    def formatCompletionItem(item: CompletionItem, @unused context: SDLBContext, @unused params: CompletionParams): CompletionItem = item

    def getIndentForDepth(depth: Int): String =
        if depth < 0 then ""
        else indent * depth
