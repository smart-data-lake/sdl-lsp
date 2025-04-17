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
import io.smartdatalake.languageserver.workspace.WorkspaceContext
import io.smartdatalake.context.SDLBContext
import scala.util.Try

trait AppModule:
  lazy val schemaReader: SchemaReader = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json")
  lazy val contextAdvisor: ContextAdvisor = new ContextAdvisorImpl
  lazy val completionEngine: SDLBCompletionEngine = new SDLBCompletionEngineImpl(schemaReader, contextAdvisor)
  lazy val hoverEngine: SDLBHoverEngine = new SDLBHoverEngineImpl(schemaReader)

  // AI Completion: Prefers colder start over slow first completion response: so no lazy val here
  val modelClient: ModelClient = new GeminiClient(Option(System.getenv("GOOGLE_API_KEY")))
  lazy val ioExecutorService: ExecutorService = Executors.newCachedThreadPool()
  lazy val ioExecutionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(ioExecutorService)
  val aiCompletionEngine: AICompletionEngineImpl = new AICompletionEngineImpl(modelClient)(using ioExecutionContext)

  lazy val serviceExecutorService: ExecutorService = Executors.newCachedThreadPool()
  lazy val serviceExecutionContext: ExecutionContext & ExecutorService = ExecutionContext.fromExecutorService(serviceExecutorService)
  lazy given ExecutionContext = serviceExecutionContext

  lazy val textDocumentService: TextDocumentService & WorkspaceContext & ClientAware = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, aiCompletionEngine)
  lazy val workspaceService: WorkspaceService = new SmartDataLakeWorkspaceService
  lazy val configurator: Configurator = new Configurator(aiCompletionEngine)
  lazy val languageServer: LanguageServer & LanguageClientAware = new SmartDataLakeLanguageServer(textDocumentService, workspaceService, configurator)

class Configurator(aiCompletionEngine: AICompletionEngineImpl):
  def configureApp(lspConfig: SDLBContext): Unit =
    aiCompletionEngine.tabStopsPrompt = Try(lspConfig.rootConfig.getString("tabStopsPrompt")).toOption

