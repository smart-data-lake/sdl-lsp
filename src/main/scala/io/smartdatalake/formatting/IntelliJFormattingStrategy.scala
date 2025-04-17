package io.smartdatalake.formatting

import org.eclipse.lsp4j.{CompletionItem, InsertTextMode}
import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.CompletionParams
import scala.annotation.tailrec

class IntelliJFormattingStrategy extends FormattingStrategy:
  override def formatCompletionItem(item: CompletionItem, context: SDLBContext, params: CompletionParams): CompletionItem =
    val arrayText = context.textContext.originalText.split("\n")

    @tailrec
    def takePrecedingBlankChars(line: Int, column: Int, blankChars: String = ""): (String, Char) = (line, column) match
        case (0, 0) => (blankChars, ' ')
        case (_, 0) => takePrecedingBlankChars(line - 1, arrayText(line - 1).length, "\n" + blankChars)
        case (_, _) =>
            val currentChar = arrayText(line).charAt(column - 1)
            if currentChar == ' ' || currentChar == '\t' then
            takePrecedingBlankChars(line, column - 1,  currentChar.toString + blankChars)
            else
            (blankChars, currentChar)
    
    val correctedLine = Math.min(params.getPosition().getLine(), arrayText.length)
    val (precedingBlankChars, lastNotBlankChar) = takePrecedingBlankChars(correctedLine, params.getPosition().getCharacter())
    val correctedDepth = if lastNotBlankChar == '{' || lastNotBlankChar == '[' then context.parentPath.size - 1 else context.parentPath.size
    val correctedPrefix = precedingBlankChars.replace("\n" + getIndentForDepth(correctedDepth), "\n")
    item.setInsertText(correctedPrefix + item.getInsertText)
    item.setInsertTextMode(InsertTextMode.AdjustIndentation)
    item

