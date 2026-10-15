package io.smartdatalake.languageserver

import io.smartdatalake.client.ClientAware
import io.smartdatalake.completion.{CompletionData, SDLBCompletionEngine}
import io.smartdatalake.completion.ai.AICompletionEngine
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.formatting.{FormattingStrategy, FormattingStrategyFactory}
import io.smartdatalake.hover.SDLBHoverEngine
import io.smartdatalake.logging.SDLBLogger
import io.smartdatalake.utils.LruCache
import com.typesafe.config.ConfigRenderOptions
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind, CompletionList, CompletionParams,
  DidChangeTextDocumentParams, DidCloseTextDocumentParams, DidOpenTextDocumentParams, DidSaveTextDocumentParams,
  Hover, HoverParams, InsertTextMode}

import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import workspace.WorkspaceManager

class SmartDataLakeTextDocumentService(
      private val completionEngine: SDLBCompletionEngine,
      private val hoverEngine: SDLBHoverEngine,
      private val aiCompletionEngine: AICompletionEngine,
      private val workspaceManager: WorkspaceManager)(using ExecutionContext)
      extends TextDocumentService
      with ClientAware with SDLBLogger {
  import workspaceManager.{getContext, isLSPConfigUri, defaultLSPConfigText}

  // The client type is only known after the initialize request
  private def formattingStrategy: FormattingStrategy = FormattingStrategyFactory.createFormattingStrategy(clientType)
  // AI completions of the most recent completion requests, keyed by insert text, parent path and hash of the context
  private val aiCompletions = LruCache[(String, String, Int), Future[String]](maxEntries = 100)
  // Context of the last completion request, used when resolving one of its items
  @volatile private var lastAIContext: String = ""
  private val AI_COMPLETION_TIMEOUT = 3.seconds

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
        // Rendered once per request, and only when needed
        val aiContext = caretContext.textContext.rootConfig.root().render(ConfigRenderOptions.concise())
        lastAIContext = aiContext
        // Precompute the most likely items: resolving them is then immediate
        formattedCompletionItems.take(3).foreach(item => tabStopsData(item).foreach(aiInsertText(item.getInsertText, _, aiContext)))
      
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


  private def tabStopsData(item: CompletionItem): Option[CompletionData] =
    Option(item.getData).map(_.toString).flatMap(CompletionData.fromJson).filter(_.withTabStops)

  /** AI insert text, computed only once per insert text, parent path and context. Falls back to the given insert text */
  private def aiInsertText(insertText: String, data: CompletionData, aiContext: String): Future[String] =
    val key = (insertText, data.parentPath, aiContext.hashCode)
    aiCompletions.getOrCompute(key) {
      val result = aiCompletionEngine.generateInsertTextWithTabStops(insertText, data.parentPath, aiContext)
        .recover { case NonFatal(ex) =>
          debug(s"AI inference error: ${ex.getMessage}")
          insertText
        }
      // Don't keep failures: the next request retries
      result.foreach(text => if text == insertText then aiCompletions.remove(key))
      result
    }


  override def didOpen(didOpenTextDocumentParams: DidOpenTextDocumentParams): Unit =
    val textDocument = didOpenTextDocumentParams.getTextDocument
    workspaceManager.updateContent(textDocument.getUri, textDocument.getText)

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit =
    // Full synchronization: the first content change holds the whole text
    val contentChanges = didChangeTextDocumentParams.getContentChanges
    workspaceManager.updateContent(didChangeTextDocumentParams.getTextDocument.getUri, Option(contentChanges).flatMap(_.toScala.headOption).map(_.getText).getOrElse(""))

  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit =
    workspaceManager.close(didCloseTextDocumentParams.getTextDocument.getUri)

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit =
    workspaceManager.save(didSaveTextDocumentParams.getTextDocument.getUri)

  override def resolveCompletionItem(completionItem: CompletionItem): CompletableFuture[CompletionItem] =
    Option.when(aiCompletionEngine.isEnabled)(completionItem).flatMap(tabStopsData) match
      case None => CompletableFuture.completedFuture(completionItem)
      case Some(data) =>
        val insertText = completionItem.getInsertText
        val aiInsertTextFuture = aiInsertText(insertText, data, lastAIContext)
        // If the AI is too slow, keep the default insert text
        val timeout = CompletableFuture.supplyAsync(() => insertText, CompletableFuture.delayedExecutor(AI_COMPLETION_TIMEOUT.toMillis, TimeUnit.MILLISECONDS)).toScala
        Future.firstCompletedOf(List(aiInsertTextFuture, timeout)).map { newInsertText =>
          if newInsertText != insertText then
            completionItem.setInsertText(newInsertText)
            completionItem.setInsertTextMode(InsertTextMode.AdjustIndentation)
          else
            debug("No AI completion available in time, using default completion")
          completionItem
        }.toJava

  override def hover(params: HoverParams): CompletableFuture[Hover] = Future {
    val context = getContext(params.getTextDocument.getUri)
    val hoverContext = context.withCaretPosition(params.getPosition.getLine + 1, params.getPosition.getCharacter)
    trace(s"Attempt to hover with hoverContext=$hoverContext")
    hoverEngine.generateHoveringInformation(hoverContext).orNull
  }.toJava

}
