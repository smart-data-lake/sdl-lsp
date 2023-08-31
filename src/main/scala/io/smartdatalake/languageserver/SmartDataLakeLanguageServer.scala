package io.smartdatalake.languageserver

import io.smartdatalake.languageserver.{SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import org.eclipse.lsp4j.services.*
import org.eclipse.lsp4j.*

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}

class SmartDataLakeLanguageServer(private val textDocumentService: TextDocumentService, private val workspaceService: WorkspaceService)(using ExecutionContext) extends LanguageServer with LanguageClientAware {
  
  private var client: Option[LanguageClient] = None
  private var errorCode = 1

  override def initialize(initializeParams: InitializeParams): CompletableFuture[InitializeResult] = {
    val initializeResult = InitializeResult(ServerCapabilities())
    initializeResult.getCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    val completionOptions = CompletionOptions()
    initializeResult.getCapabilities.setCompletionProvider(completionOptions)

    initializeResult.getCapabilities.setHoverProvider(true)
    Future(initializeResult).toJava
  }

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
