package io.smartdatalake

import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.{CodeAction, CodeActionParams, CodeLens, CodeLensParams, Command, CompletionItem, CompletionItemKind, CompletionList, CompletionParams, DefinitionParams, DidChangeTextDocumentParams, DidCloseTextDocumentParams, DidOpenTextDocumentParams, DidSaveTextDocumentParams, DocumentFormattingParams, DocumentHighlight, DocumentHighlightParams, DocumentOnTypeFormattingParams, DocumentRangeFormattingParams, DocumentSymbol, DocumentSymbolParams, Hover, HoverParams, InsertReplaceEdit, Location, LocationLink, Range, ReferenceParams, RenameParams, SignatureHelp, SignatureHelpParams, SymbolInformation, TextDocumentPositionParams, TextEdit, WorkspaceEdit, Position}
import org.eclipse.lsp4j.services.TextDocumentService

import java.util
import java.util.concurrent.CompletableFuture

class SmartDataLakeTextDocumentService extends TextDocumentService {

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = {

    CompletableFuture.supplyAsync(() => {
      val completionItems = new util.ArrayList[CompletionItem]()

      if (params.getPosition.getLine == 1) {
        val completionItem = new CompletionItem()
        completionItem.setInsertText("dataObjects {\n\t\n}\n\nactions {\n\t\n}")
        completionItem.setLabel("gen")
        completionItem.setKind(CompletionItemKind.Snippet)
        completionItem.setDetail("Generate basic template")

        completionItems.add(completionItem)
      } else {
        // Sample Completion item for dataObject
        val completionItem = new CompletionItem()
        // Define the text to be inserted in to the file if the completion item is selected.
        completionItem.setInsertText("dataObjects")
        // Set the label that shows when the completion drop down appears in the Editor.
        completionItem.setLabel("dataObjects")
        // Set the completion kind. This is a snippet.
        // That means it replace character which trigger the completion and
        // replace it with what defined in inserted text.
        completionItem.setKind(CompletionItemKind.Snippet)
        // This will set the details for the snippet code which will help user to
        // understand what this completion item is.
        completionItem.setDetail(" {...}\n Defines the data objects")
        // Add the sample completion item to the list.
        completionItems.add(completionItem)
      }

      messages.Either.forLeft(completionItems).asInstanceOf[messages.Either[util.List[CompletionItem], CompletionList]]
    })
  }

  override def didOpen(didOpenTextDocumentParams: DidOpenTextDocumentParams): Unit = ???

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit = ???

  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit = ???

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit = ???

  override def resolveCompletionItem(completionItem: CompletionItem): CompletableFuture[CompletionItem] = ???

  override def hover(params: HoverParams): CompletableFuture[Hover] = super.hover(params)

  override def signatureHelp(params: SignatureHelpParams): CompletableFuture[SignatureHelp] = super.signatureHelp(params)

  override def definition(params: DefinitionParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = super.definition(params)

  override def references(referenceParams: ReferenceParams): CompletableFuture[util.List[_ <: Location]] = ???

  override def documentHighlight(params: DocumentHighlightParams): CompletableFuture[util.List[_ <: DocumentHighlight]] = super.documentHighlight(params)

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[util.List[messages.Either[SymbolInformation, DocumentSymbol]]] = super.documentSymbol(params)

  override def codeAction(params: CodeActionParams): CompletableFuture[util.List[messages.Either[Command, CodeAction]]] = super.codeAction(params)

  override def codeLens(codeLensParams: CodeLensParams): CompletableFuture[util.List[_ <: CodeLens]] = ???

  override def resolveCodeLens(codeLens: CodeLens): CompletableFuture[CodeLens] = ???

  override def formatting(documentFormattingParams: DocumentFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def rangeFormatting(documentRangeFormattingParams: DocumentRangeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def onTypeFormatting(documentOnTypeFormattingParams: DocumentOnTypeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def rename(renameParams: RenameParams): CompletableFuture[WorkspaceEdit] = ???
}
