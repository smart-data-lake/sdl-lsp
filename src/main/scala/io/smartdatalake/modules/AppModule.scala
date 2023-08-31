package io.smartdatalake.modules

import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.hover.{SDLBHoverEngine, SDLBHoverEngineImpl}
import io.smartdatalake.languageserver.{SmartDataLakeLanguageServer, SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.schema.{SchemaReader, SchemaReaderImpl}
import org.eclipse.lsp4j.services.{LanguageClientAware, LanguageServer, TextDocumentService, WorkspaceService}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

trait AppModule {
  lazy val schemaReader: SchemaReader = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json")
  lazy val completionEngine: SDLBCompletionEngine = new SDLBCompletionEngineImpl(schemaReader)
  lazy val hoverEngine: SDLBHoverEngine = new SDLBHoverEngineImpl(schemaReader)
  lazy val executorService: ExecutorService = Executors.newCachedThreadPool()
  lazy val executionContext: ExecutionContext & ExecutorService = ExecutionContext.fromExecutorService(executorService)
  lazy val textDocumentService: TextDocumentService = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine)(using executionContext)
  lazy val workspaceService: WorkspaceService = new SmartDataLakeWorkspaceService
  lazy val languageServer: LanguageServer & LanguageClientAware = new SmartDataLakeLanguageServer(textDocumentService, workspaceService)(using executionContext)

}

