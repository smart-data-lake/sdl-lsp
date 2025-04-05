package io.smartdatalake.modules

import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.context.{ContextAdvisor, ContextAdvisorImpl}
import io.smartdatalake.hover.{SDLBHoverEngine, SDLBHoverEngineImpl}
import io.smartdatalake.languageserver.{SmartDataLakeLanguageServer, SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.schema.{SchemaReader, SchemaReaderImpl}
import io.smartdatalake.client.ClientAware
import org.eclipse.lsp4j.services.{LanguageClientAware, LanguageServer, TextDocumentService, WorkspaceService}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.concurrent.ExecutionContext.Implicits.global
import io.smartdatalake.completion.ai.model.{ModelClient, GeminiClient}
import io.smartdatalake.completion.ai.{AICompletionEngine, AICompletionEngineImpl}

trait AppModule {
  lazy val schemaReader: SchemaReader = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json")
  lazy val contextAdvisor: ContextAdvisor = new ContextAdvisorImpl
  lazy val completionEngine: SDLBCompletionEngine = new SDLBCompletionEngineImpl(schemaReader, contextAdvisor)
  lazy val hoverEngine: SDLBHoverEngine = new SDLBHoverEngineImpl(schemaReader)
  lazy val executorService: ExecutorService = Executors.newCachedThreadPool()
  lazy val executionContext: ExecutionContext & ExecutorService = ExecutionContext.fromExecutorService(executorService)
  lazy given ExecutionContext = executionContext

  // AI Completion: Prefers colder start over slow first completion response
  val modelClient: ModelClient = new GeminiClient(Option(System.getenv("GOOGLE_API_KEY")))
  val aiCompletionEngine: AICompletionEngine = new AICompletionEngineImpl(modelClient)

  lazy val textDocumentService: TextDocumentService & ClientAware = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, aiCompletionEngine)
  lazy val workspaceService: WorkspaceService = new SmartDataLakeWorkspaceService
  lazy val languageServer: LanguageServer & LanguageClientAware = new SmartDataLakeLanguageServer(textDocumentService, workspaceService)

}

