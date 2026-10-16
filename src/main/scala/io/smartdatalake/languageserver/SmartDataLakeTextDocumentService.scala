package io.smartdatalake.languageserver

import io.smartdatalake.client.ClientAware
import io.smartdatalake.completion.SDLBCompletionEngine
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.formatting.{FormattingStrategy, FormattingStrategyFactory}
import io.smartdatalake.hover.SDLBHoverEngine
import io.smartdatalake.logging.SDLBLogger
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.{LanguageClient, LanguageClientAware, TextDocumentService}
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind, CompletionList, CompletionParams,
  DidChangeTextDocumentParams, DidCloseTextDocumentParams, DidOpenTextDocumentParams, DidSaveTextDocumentParams,
  DefinitionParams, DocumentSymbol, PublishDiagnosticsParams, DocumentSymbolParams, Hover, HoverParams, Location, LocationLink, ReferenceParams, SymbolInformation}

import java.util
import java.util.concurrent.{CompletableFuture, Executors, ScheduledFuture, TimeUnit}
import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
import workspace.WorkspaceManager
import io.smartdatalake.navigation.NavigationEngine
import io.smartdatalake.diagnostics.DiagnosticsEngine

class SmartDataLakeTextDocumentService(
      private val completionEngine: SDLBCompletionEngine,
      private val hoverEngine: SDLBHoverEngine,
      private val workspaceManager: WorkspaceManager,
      private val navigationEngine: NavigationEngine,
      private val diagnosticsEngine: DiagnosticsEngine)(using ExecutionContext)
      extends TextDocumentService
      with LanguageClientAware with ClientAware with SDLBLogger {
  import workspaceManager.{getContext, isLSPConfigUri, defaultLSPConfigText}

  // The client type is only known after the initialize request
  private def formattingStrategy: FormattingStrategy = FormattingStrategyFactory.createFormattingStrategy(clientType)

  @volatile private var client: Option[LanguageClient] = None
  private val diagnosticsScheduler = Executors.newSingleThreadScheduledExecutor { runnable =>
    val thread = Thread(runnable, "diagnostics")
    thread.setDaemon(true)
    thread
  }
  private val pendingDiagnostics = TrieMap.empty[String, ScheduledFuture[?]]

  override def connect(languageClient: LanguageClient): Unit = client = Option(languageClient)

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = Future {
    val uri = params.getTextDocument.getUri
    if isLSPConfigUri(uri) then
      Left(generateLSPConfigCompletions(params.getPosition.getLine).toJava).toJava
    else
      val context = getContext(uri)
      val caretContext = context.withCaretPosition(params.getPosition.getLine+1, params.getPosition.getCharacter)
      val completionItems: List[CompletionItem] = completionEngine.generateCompletionItems(caretContext)
      val formattedCompletionItems = completionItems.map(formattingStrategy.formatCompletionItem(_, caretContext, params))
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

  override def didOpen(didOpenTextDocumentParams: DidOpenTextDocumentParams): Unit =
    val textDocument = didOpenTextDocumentParams.getTextDocument
    workspaceManager.updateContent(textDocument.getUri, textDocument.getText)
    scheduleDiagnostics(textDocument.getUri, 200.milliseconds)

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit =
    // Full synchronization: the first content change holds the whole text
    val contentChanges = didChangeTextDocumentParams.getContentChanges
    val uri = didChangeTextDocumentParams.getTextDocument.getUri
    workspaceManager.updateContent(uri, Option(contentChanges).flatMap(_.toScala.headOption).map(_.getText).getOrElse(""))
    // Wait for the user to stop typing
    scheduleDiagnostics(uri, 500.milliseconds)

  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit =
    val uri = didCloseTextDocumentParams.getTextDocument.getUri
    workspaceManager.close(uri)
    pendingDiagnostics.remove(uri).foreach(_.cancel(false))
    client.foreach(_.publishDiagnostics(PublishDiagnosticsParams(uri, util.List.of())))

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit =
    workspaceManager.save(didSaveTextDocumentParams.getTextDocument.getUri)
    // References in the other documents may have changed
    workspaceManager.openDocuments.foreach(scheduleDiagnostics(_, 0.milliseconds))

  private def scheduleDiagnostics(uri: String, delay: FiniteDuration): Unit =
    if client.isDefined && !isLSPConfigUri(uri) then
      val task = diagnosticsScheduler.schedule((() => publishDiagnostics(uri)): Runnable, delay.toMillis, TimeUnit.MILLISECONDS)
      pendingDiagnostics.put(uri, task).foreach(_.cancel(false))

  private def publishDiagnostics(uri: String): Unit =
    Try {
      val visibleFiles = workspaceManager.visibleFiles(uri)
      visibleFiles.find(_.uri == uri).foreach { file =>
        val diagnostics = diagnosticsEngine.diagnostics(file, visibleFiles)
        client.foreach(_.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics.toJava)))
      }
    }.failed.foreach(ex => warn(s"Error while computing the diagnostics of $uri: ${ex.getMessage}"))

  override def definition(params: DefinitionParams): CompletableFuture[messages.Either[util.List[? <: Location], util.List[? <: LocationLink]]] = Future {
    val uri = params.getTextDocument.getUri
    val context = getContext(uri).withCaretPosition(params.getPosition.getLine + 1, params.getPosition.getCharacter)
    messages.Either.forLeft[util.List[? <: Location], util.List[? <: LocationLink]](navigationEngine.definitions(context, workspaceManager.visibleFiles(uri)).toJava)
  }.toJava

  override def references(params: ReferenceParams): CompletableFuture[util.List[? <: Location]] = Future {
    val uri = params.getTextDocument.getUri
    val context = getContext(uri).withCaretPosition(params.getPosition.getLine + 1, params.getPosition.getCharacter)
    val includeDeclaration = Option(params.getContext).exists(_.isIncludeDeclaration)
    navigationEngine.references(context, workspaceManager.visibleFiles(uri), includeDeclaration).toJava: util.List[? <: Location]
  }.toJava

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[util.List[messages.Either[SymbolInformation, DocumentSymbol]]] = Future {
    val uri = params.getTextDocument.getUri
    workspaceManager.visibleFiles(uri).find(_.uri == uri).toList
      .flatMap(navigationEngine.documentSymbols)
      .map(symbol => messages.Either.forRight[SymbolInformation, DocumentSymbol](symbol))
      .toJava
  }.toJava

  override def hover(params: HoverParams): CompletableFuture[Hover] = Future {
    val context = getContext(params.getTextDocument.getUri)
    val hoverContext = context.withCaretPosition(params.getPosition.getLine + 1, params.getPosition.getCharacter)
    trace(s"Attempt to hover with hoverContext=$hoverContext")
    hoverEngine.generateHoveringInformation(hoverContext).orNull
  }.toJava

}
