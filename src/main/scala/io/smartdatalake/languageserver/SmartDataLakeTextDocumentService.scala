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

import java.util
import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration.*
import scala.concurrent.Await
import scala.io.Source
import scala.util.Using
import io.smartdatalake.logging.SDLBLogger
import io.smartdatalake.formatting.{FormattingStrategy, FormattingStrategyFactory}
import org.eclipse.lsp4j.CompletionTriggerKind
import io.smartdatalake.completion.ai.AICompletionEngine
import io.smartdatalake.completion.CompletionData
import scala.collection.concurrent.TrieMap
import scala.util.Try
import workspace.WorkspaceContext

class SmartDataLakeTextDocumentService(
      private val completionEngine: SDLBCompletionEngine,
      private val hoverEngine: SDLBHoverEngine,
      private val aiCompletionEngine: AICompletionEngine)(using ExecutionContext)
      extends TextDocumentService
      with WorkspaceContext with ClientAware with SDLBLogger {

  private lazy val formattingStrategy: FormattingStrategy = FormattingStrategyFactory.createFormattingStrategy(clientType)
  private val precomputedCompletions: TrieMap[String, Future[String]] = TrieMap.empty

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = Future {
    val uri = params.getTextDocument.getUri
    if isLSPConfigUri(uri) then
      Left(generateLSPConfigCompletions(params.getPosition.getLine).toJava).toJava
    else
      val context = getContext(uri)
      val caretContext = context.withCaretPosition(params.getPosition.getLine+1, params.getPosition.getCharacter)
      val completionItems: List[CompletionItem] = completionEngine.generateCompletionItems(caretContext)
      val formattedCompletionItems = completionItems.map(formattingStrategy.formatCompletionItem(_, caretContext, params))
      
      if aiCompletionEngine.isEnabled then
        precomputedCompletions.clear()
        formattedCompletionItems.take(3).foreach(generateAICompletions)
      
      Left(formattedCompletionItems.toJava).toJava
  }.toJava

  private def generateLSPConfigCompletions(line: Int): List[CompletionItem] =
    if line <= 1 then
      val text = defaultLSPConfigText.getOrElse("")
      val completionItem = new CompletionItem()
      completionItem.setLabel("Default Template")
      completionItem.setDetail("Default Template")
      completionItem.setInsertText(text)
      completionItem.setKind(CompletionItemKind.Snippet)
      List(completionItem)
    else
      List.empty[CompletionItem]


  private def generateAICompletions(item: CompletionItem): Unit =
    Option(item.getData).map(_.toString).flatMap(CompletionData.fromJson).filter(_.withTabStops).foreach { data =>
      val result = aiCompletionEngine
        .generateInsertTextWithTabStops(item.getInsertText, data.parentPath, data.context)
        .recover {
          case ex: Exception =>
            debug(s"AI inference error: ${ex.getMessage}")
            item.getInsertText // Fallback to original text
        }
      precomputedCompletions += (item.getInsertText -> result)
    }


  override def didOpen(didOpenTextDocumentParams: DidOpenTextDocumentParams): Unit =
    val uri = didOpenTextDocumentParams.getTextDocument.getUri
    val content = didOpenTextDocumentParams.getTextDocument.getText
    insert(uri, content)
    updateWorkspace(uri)

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit =
    val contentChanges = didChangeTextDocumentParams.getContentChanges
    update(didChangeTextDocumentParams.getTextDocument.getUri, Option(contentChanges).flatMap(_.toScala.headOption).map(_.getText).getOrElse(""))


  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit =
    val uri = didCloseTextDocumentParams.getTextDocument.getUri
    if isUriDeleted(uri) then
      info(s"Detecting deletion of $uri")
      update(uri, "")
    updateWorkspace(uri)

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit =
    updateWorkspace(didSaveTextDocumentParams.getTextDocument.getUri)

  override def resolveCompletionItem(completionItem: CompletionItem): CompletableFuture[CompletionItem] = Future {
    if aiCompletionEngine.isEnabled then
      
      Option(completionItem.getData).map(_.toString).flatMap(CompletionData.fromJson).foreach { data =>
        if data.withTabStops then
          precomputedCompletions.get(completionItem.getInsertText) match
            case Some(future) =>
              Try {
                // Try to get result with timeout
                val result = Await.result(future, 3000.milliseconds)
                completionItem.setInsertText(result)
                completionItem.setInsertTextMode(InsertTextMode.AdjustIndentation)
              }.recover {
                case _: java.util.concurrent.TimeoutException =>
                  // If timeout, don't modify the insert text - use default
                  debug("AI completion inference timeout, using default completion")
                case ex: Exception =>
                  debug(s"Error during AI completion: ${ex.getMessage}")
              }
            
            case None =>
              val futureInsertText = aiCompletionEngine.generateInsertTextWithTabStops(completionItem.getInsertText, data.parentPath, data.context)
              val insertText = Await.result(futureInsertText, 3000.milliseconds)
              completionItem.setInsertText(insertText)
              completionItem.setInsertTextMode(InsertTextMode.AdjustIndentation)
      }
    
    completionItem
  }.toJava

  override def hover(params: HoverParams): CompletableFuture[Hover] = {
    Future {
      val context = getContext(params.getTextDocument.getUri)
      val hoverContext = context.withCaretPosition(params.getPosition.getLine + 1, params.getPosition.getCharacter)
      trace(s"Attempt to hover with hoverContext=$hoverContext")
      hoverEngine.generateHoveringInformation(hoverContext)
    }.toJava
  }

  override def signatureHelp(params: SignatureHelpParams): CompletableFuture[SignatureHelp] = super.signatureHelp(params)

  /**
    * To be used to navigate to the definition of a field
    *
    * @param params
    * @return
    */
  override def definition(params: DefinitionParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = super.definition(params)

  /**
    * 
    * To be used to navigate to the reference of a field: the other way around than definition
    *
    * @param referenceParams
    * @return
    */
  override def references(referenceParams: ReferenceParams): CompletableFuture[util.List[_ <: Location]] = ???

  override def documentHighlight(params: DocumentHighlightParams): CompletableFuture[util.List[_ <: DocumentHighlight]] = super.documentHighlight(params)

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[util.List[messages.Either[SymbolInformation, DocumentSymbol]]] = super.documentSymbol(params)

  /**
    * To be used to suggest missing required fields for example
    *
    * @param params
    * @return
    */
  override def codeAction(params: CodeActionParams): CompletableFuture[util.List[messages.Either[Command, CodeAction]]] = super.codeAction(params)

  override def codeLens(codeLensParams: CodeLensParams): CompletableFuture[util.List[_ <: CodeLens]] = ???

  override def resolveCodeLens(codeLens: CodeLens): CompletableFuture[CodeLens] = ???

  /**
    * To be used to format the document and to retrieve the tab size
    *
    * @param documentFormattingParams
    * @return
    */
  override def formatting(documentFormattingParams: DocumentFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def rangeFormatting(documentRangeFormattingParams: DocumentRangeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def onTypeFormatting(documentOnTypeFormattingParams: DocumentOnTypeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def rename(renameParams: RenameParams): CompletableFuture[WorkspaceEdit] = ???
}
