package io.smartdatalake.languageserver

import io.smartdatalake.client.ClientAware
import io.smartdatalake.completion.{CompletionData, SDLBCompletionEngine}
import io.smartdatalake.completion.ai.AICompletionEngine
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.formatting.{FormattingStrategy, FormattingStrategyFactory}
import io.smartdatalake.hover.SDLBHoverEngine
import io.smartdatalake.logging.SDLBLogger
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind, CompletionList, CompletionParams,
  DidChangeTextDocumentParams, DidCloseTextDocumentParams, DidOpenTextDocumentParams, DidSaveTextDocumentParams,
  Hover, HoverParams, InsertTextMode}

import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.collection.concurrent.TrieMap
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

  private lazy val formattingStrategy: FormattingStrategy = FormattingStrategyFactory.createFormattingStrategy(clientType)
  private val precomputedCompletions: TrieMap[String, Future[String]] = TrieMap.empty
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
    val textDocument = didOpenTextDocumentParams.getTextDocument
    workspaceManager.updateContent(textDocument.getUri, textDocument.getText)

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit =
    // Full synchronization: the first content change holds the whole text
    val contentChanges = didChangeTextDocumentParams.getContentChanges
    workspaceManager.updateContent(didChangeTextDocumentParams.getTextDocument.getUri, Option(contentChanges).flatMap(_.toScala.headOption).map(_.getText).getOrElse(""))

  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit =
    workspaceManager.close(didCloseTextDocumentParams.getTextDocument.getUri)

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit = ()

  override def resolveCompletionItem(completionItem: CompletionItem): CompletableFuture[CompletionItem] =
    val tabStopsData = Option.when(aiCompletionEngine.isEnabled)(completionItem.getData).flatMap(Option(_))
      .map(_.toString)
      .flatMap(CompletionData.fromJson)
      .filter(_.withTabStops)
    tabStopsData match
      case None => CompletableFuture.completedFuture(completionItem)
      case Some(data) =>
        val insertText = completionItem.getInsertText
        val aiInsertText = precomputedCompletions
          .getOrElse(insertText, aiCompletionEngine.generateInsertTextWithTabStops(insertText, data.parentPath, data.context))
          .recover { case NonFatal(ex) =>
            debug(s"Error during AI completion: ${ex.getMessage}")
            insertText
          }
        // If the AI is too slow, keep the default insert text
        val timeout = CompletableFuture.supplyAsync(() => insertText, CompletableFuture.delayedExecutor(AI_COMPLETION_TIMEOUT.toMillis, TimeUnit.MILLISECONDS)).toScala
        Future.firstCompletedOf(List(aiInsertText, timeout)).map { newInsertText =>
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
