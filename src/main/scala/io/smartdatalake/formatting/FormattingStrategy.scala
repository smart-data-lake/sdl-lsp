package io.smartdatalake.formatting

import org.eclipse.lsp4j.CompletionItem
import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.CompletionParams

trait FormattingStrategy:
    val indentSize: Int = 2
    lazy val indent: String = " " * indentSize

    def formatCompletionItem(item: CompletionItem, context: SDLBContext, params: CompletionParams): CompletionItem = item

    def getIndentForDepth(depth: Int): String =
        if depth < 0 then ""
        else indent * depth
