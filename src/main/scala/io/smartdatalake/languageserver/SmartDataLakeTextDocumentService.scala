package io.smartdatalake.languageserver

import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.hover.{SDLBHoverEngine, SDLBHoverEngineImpl}
import io.smartdatalake.schema.SchemaReader
import io.smartdatalake.client.{ClientAware, ClientType}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.{CodeAction, CodeActionParams, CodeLens, CodeLensParams,
  Command, CompletionItem, CompletionItemKind, CompletionList, CompletionParams,
  DefinitionParams, DidChangeTextDocumentParams, DidCloseTextDocumentParams,
  DidOpenTextDocumentParams, DidSaveTextDocumentParams, DocumentFormattingParams,
  DocumentHighlight, DocumentHighlightParams, DocumentOnTypeFormattingParams,
  DocumentRangeFormattingParams, DocumentSymbol, DocumentSymbolParams, Hover,
  HoverParams, InsertReplaceEdit, Location, LocationLink, MarkupContent, MarkupKind,
  Position, Range, ReferenceParams, RenameParams, SignatureHelp, SignatureHelpParams,
  SymbolInformation, TextDocumentPositionParams, TextEdit, WorkspaceEdit, InsertTextMode}
import org.slf4j.LoggerFactory

import java.util
import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Using

class SmartDataLakeTextDocumentService(private val completionEngine: SDLBCompletionEngine,
      private val hoverEngine: SDLBHoverEngine)(using ExecutionContext)
      extends TextDocumentService with ClientAware {

  private var uriToContextMap: Map[String, SDLBContext] = Map("" -> SDLBContext.EMPTY_CONTEXT)

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = {
    import ClientType.*
    Future {
      val context = uriToContextMap(params.getTextDocument.getUri)
      val caretContext = context.withCaretPosition(params.getPosition.getLine+1, params.getPosition.getCharacter)
      val completionItems: List[CompletionItem] = completionEngine.generateCompletionItems(caretContext)
      val completionItemsClientAware = completionItems.map { item => clientType match
        case IntelliJ =>
          item.setInsertTextMode(InsertTextMode.AsIs)
          item
        case VSCode | Unknown => 
          item.setInsertTextMode(InsertTextMode.AdjustIndentation)
          item
      }
      
      Left(completionItemsClientAware.toJava).toJava
    }.toJava

  }

  override def didOpen(didOpenTextDocumentParams: DidOpenTextDocumentParams): Unit =
    uriToContextMap += (didOpenTextDocumentParams.getTextDocument.getUri, SDLBContext.fromText(didOpenTextDocumentParams.getTextDocument.getText))

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit =
    val contentChanges = didChangeTextDocumentParams.getContentChanges
    val context = uriToContextMap(didChangeTextDocumentParams.getTextDocument.getUri)
    val newContext =
      if contentChanges != null && contentChanges.size() > 0 then
        // Update the stored document content with the new content. Assuming Full sync technique
        context.withText(contentChanges.get(0).getText)
      else
        context
    uriToContextMap += (didChangeTextDocumentParams.getTextDocument.getUri, newContext)


  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit = uriToContextMap -= didCloseTextDocumentParams.getTextDocument.getUri

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit = ???

  override def resolveCompletionItem(completionItem: CompletionItem): CompletableFuture[CompletionItem] = ???

  override def hover(params: HoverParams): CompletableFuture[Hover] = {
    Future {
      val context = uriToContextMap(params.getTextDocument.getUri)
      val hoverContext = context.withCaretPosition(params.getPosition.getLine + 1, params.getPosition.getCharacter)
      val logger = LoggerFactory.getLogger(getClass)
      logger.trace("Attempt to hover with hoverContext={}", hoverContext)
      hoverEngine.generateHoveringInformation(hoverContext)
    }.toJava
  }

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
