package io.smartdatalake

import org.eclipse.lsp4j.{CompletionOptions, InitializeParams, InitializeResult, ServerCapabilities, TextDocumentSyncKind}
import org.eclipse.lsp4j.services.{LanguageClient, LanguageClientAware, LanguageServer, TextDocumentService, WorkspaceService}

import java.util.concurrent.CompletableFuture

class SmartDataLakeLanguageServer extends LanguageServer with LanguageClientAware {

  private val textDocumentService = new SmartDataLakeTextDocumentService
  private val workspaceService = new SmartDataLakeWorkspaceService
  private var client: Option[LanguageClient] = None
  private var errorCode = 1

  override def initialize(initializeParams: InitializeParams): CompletableFuture[InitializeResult] = {
    val initializeResult = new InitializeResult(new ServerCapabilities)
    initializeResult.getCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    val completionOptions = new CompletionOptions
    initializeResult.getCapabilities.setCompletionProvider(completionOptions)
    CompletableFuture.supplyAsync(() => initializeResult)
  }

  override def shutdown(): CompletableFuture[AnyRef] = {
    errorCode = 0
    null
  }

  override def exit(): Unit = System.exit(errorCode)

  override def getTextDocumentService: TextDocumentService = textDocumentService

  override def getWorkspaceService: WorkspaceService = workspaceService


  override def connect(languageClient: LanguageClient): Unit = {
    client = Some(languageClient)
  }
  
  def getErrorCode: Int = errorCode

}
