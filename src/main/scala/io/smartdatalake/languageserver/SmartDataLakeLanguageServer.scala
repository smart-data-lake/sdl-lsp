package io.smartdatalake.languageserver

import io.smartdatalake.languageserver.{SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.services.*
import org.eclipse.lsp4j.*

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}
import io.smartdatalake.context.SDLBContext
import workspace.WorkspaceContext
import io.smartdatalake.modules.Configurator

class SmartDataLakeLanguageServer(
  private val textDocumentService: TextDocumentService & WorkspaceContext,
  private val workspaceService: WorkspaceService,
  private val configurator: Configurator)(using ExecutionContext) extends LanguageServer with LanguageClientAware {
  
  private var client: Option[LanguageClient] = None
  private var errorCode = 1

  override def initialize(initializeParams: InitializeParams): CompletableFuture[InitializeResult] = {
    initializeWorkspaces(initializeParams)  
    val initializeResult = InitializeResult(ServerCapabilities())
    initializeResult.getCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    val completionOptions = CompletionOptions()
    completionOptions.setResolveProvider(true)
    initializeResult.getCapabilities.setCompletionProvider(completionOptions)

    initializeResult.getCapabilities.setHoverProvider(true)
    Future(initializeResult).toJava
  }

  private def initializeWorkspaces(initializeParams: InitializeParams): Unit =
    val rootUri = Option(initializeParams).flatMap(_.getWorkspaceFolders
      .toScala
      .headOption
      .map(_.getUri))
      .getOrElse("")
    val lspConfig = textDocumentService.initializeWorkspaces(rootUri)
    configurator.configureApp(lspConfig)


  override def shutdown(): CompletableFuture[AnyRef] = {
    errorCode = 0
    CompletableFuture.completedFuture(null)
  }

  override def exit(): Unit = System.exit(errorCode)

  override def getTextDocumentService: TextDocumentService = textDocumentService

  override def getWorkspaceService: WorkspaceService = workspaceService


  override def connect(languageClient: LanguageClient): Unit = {
    client = Some(languageClient)
  }
  
  def getErrorCode: Int = errorCode

}
