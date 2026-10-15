package io.smartdatalake.languageserver

import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.services.*
import org.eclipse.lsp4j.*

import java.util.concurrent.CompletableFuture
import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}
import workspace.WorkspaceManager
import io.smartdatalake.modules.Configurator
import io.smartdatalake.client.{ClientAware, ClientType}
import io.smartdatalake.logging.SDLBLogger

class SmartDataLakeLanguageServer(
  private val textDocumentService: TextDocumentService & LanguageClientAware & ClientAware,
  private val workspaceManager: WorkspaceManager,
  private val workspaceService: WorkspaceService,
  private val configurator: Configurator)(using ExecutionContext) extends LanguageServer with LanguageClientAware with SDLBLogger {
  
  private var errorCode = 1
  workspaceManager.onLSPConfigLoaded(configurator.configureApp)

  override def initialize(initializeParams: InitializeParams): CompletableFuture[InitializeResult] = {
    detectClientType(initializeParams)
    initializeWorkspaces(initializeParams)  
    val initializeResult = InitializeResult(ServerCapabilities())
    initializeResult.getCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    val completionOptions = CompletionOptions()
    completionOptions.setResolveProvider(true)
    initializeResult.getCapabilities.setCompletionProvider(completionOptions)

    initializeResult.getCapabilities.setHoverProvider(true)
    initializeResult.getCapabilities.setDefinitionProvider(true)
    initializeResult.getCapabilities.setReferencesProvider(true)
    initializeResult.getCapabilities.setDocumentSymbolProvider(true)
    Future(initializeResult).toJava
  }

  /** The client type given on the command line has priority */
  private def detectClientType(initializeParams: InitializeParams): Unit =
    val clientName = Option(initializeParams).flatMap(p => Option(p.getClientInfo)).flatMap(info => Option(info.getName))
    clientName.foreach(name => info(s"Client: $name ${Option(initializeParams.getClientInfo.getVersion).getOrElse("")}"))
    if textDocumentService.clientType == ClientType.Unknown then
      textDocumentService.clientType = clientName.map(ClientType.fromClientName).getOrElse(ClientType.Unknown)

  @nowarn("cat=deprecation") // rootUri is the fallback for clients not sending workspace folders
  private def initializeWorkspaces(initializeParams: InitializeParams): Unit =
    val params = Option(initializeParams)
    val rootUri = params.flatMap(p => Option(p.getWorkspaceFolders))
      .flatMap(_.toScala.headOption.map(_.getUri))
      .orElse(params.flatMap(p => Option(p.getRootUri)))
      .getOrElse("")
    workspaceManager.initialize(rootUri)


  override def shutdown(): CompletableFuture[AnyRef] = {
    errorCode = 0
    CompletableFuture.completedFuture(null)
  }

  override def exit(): Unit = System.exit(errorCode)

  override def getTextDocumentService: TextDocumentService = textDocumentService

  override def getWorkspaceService: WorkspaceService = workspaceService


  override def connect(languageClient: LanguageClient): Unit = {
    textDocumentService.connect(languageClient)
  }
  
  def getErrorCode: Int = errorCode

}
