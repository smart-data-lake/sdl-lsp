package io.smartdatalake.formatting

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.{CompletionItem, InsertTextMode}
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.{Position, TextDocumentIdentifier}

class IntelliJFormattingStrategySpec extends UnitSpec {

    private val context = SDLBContext.fromText(loadFile("fixture/hocon/with-lists-example.conf"))
    private val params = new CompletionParams(new TextDocumentIdentifier(), new Position(0, 0))
    private val strategy = new IntelliJFormattingStrategy()
    private val item = new CompletionItem()

    "IntelliJFormattingStrategy" should "format completion item correctly without adding blank characters" in {
        item.setInsertText("CopyAction")
        params.setPosition(new Position(3, 13))
    
        val formattedItem = strategy.formatCompletionItem(item, context.withCaretPosition(3, 13), params)
    
        // Add assertions to verify the formatting
        assert(formattedItem.getInsertText == "CopyAction")
    }

    it should "format completion item after = on same line" in {
        item.setInsertText("CopyAction")
        params.setPosition(new Position(3, 12))
    
        val formattedItem = strategy.formatCompletionItem(item, context.withCaretPosition(3, 12), params)
    
        // Add assertions to verify the formatting
        assert(formattedItem.getInsertText == " CopyAction")
    }

    it should "add a new line if inside an object" in {
        item.setInsertText("inputId")
        params.setPosition(new Position(4, 4))
    
        val formattedItem = strategy.formatCompletionItem(item, context.withCaretPosition(4, 4), params)
    
        // Add assertions to verify the formatting
        assert(formattedItem.getInsertText == "\ninputId")
    }

}
