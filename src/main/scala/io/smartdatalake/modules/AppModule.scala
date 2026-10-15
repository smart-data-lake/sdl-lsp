package io.smartdatalake.modules

import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.context.{ContextAdvisor, ContextAdvisorImpl}
import io.smartdatalake.hover.{SDLBHoverEngine, SDLBHoverEngineImpl}
import io.smartdatalake.languageserver.{SmartDataLakeLanguageServer, SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.schema.SchemaReaderImpl
import io.smartdatalake.client.ClientAware
import org.eclipse.lsp4j.services.{LanguageClientAware, LanguageServer, TextDocumentService, WorkspaceService}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import io.smartdatalake.completion.ai.model.{ModelClient, OpenAICompatibleClient}
import io.smartdatalake.completion.ai.AICompletionEngineImpl
import io.smartdatalake.languageserver.workspace.WorkspaceManager
import io.smartdatalake.navigation.NavigationEngine
import io.smartdatalake.context.SDLBContext
import scala.util.Try
import java.net.URI
import java.nio.file.Paths
import io.smartdatalake.logging.LoggingManager

trait AppModule:
  lazy val schemaReader: SchemaReaderImpl = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json")
  lazy val contextAdvisor: ContextAdvisor = new ContextAdvisorImpl
  lazy val completionEngine: SDLBCompletionEngine = new SDLBCompletionEngineImpl(schemaReader, contextAdvisor)
  lazy val hoverEngine: SDLBHoverEngine = new SDLBHoverEngineImpl(schemaReader)

  // AI Completion: Prefers colder start over slow first completion response: so no lazy val here
  val modelClient: ModelClient = new OpenAICompatibleClient(OpenAICompatibleClient.apiKeyFromEnvironment)
  lazy val ioExecutorService: ExecutorService = Executors.newCachedThreadPool()
  lazy val ioExecutionContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(ioExecutorService)
  val aiCompletionEngine: AICompletionEngineImpl = new AICompletionEngineImpl(modelClient)(using ioExecutionContext)

  lazy val serviceExecutorService: ExecutorService = Executors.newCachedThreadPool()
  lazy val serviceExecutionContext: ExecutionContext & ExecutorService = ExecutionContext.fromExecutorService(serviceExecutorService)
  lazy given ExecutionContext = serviceExecutionContext

  lazy val workspaceManager: WorkspaceManager = new WorkspaceManager
  lazy val navigationEngine: NavigationEngine = new NavigationEngine
  lazy val textDocumentService: TextDocumentService & ClientAware = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, aiCompletionEngine, workspaceManager, navigationEngine)
  lazy val workspaceService: WorkspaceService = new SmartDataLakeWorkspaceService
  lazy val configurator: Configurator = new Configurator(aiCompletionEngine, modelClient, schemaReader)
  lazy val languageServer: LanguageServer & LanguageClientAware = new SmartDataLakeLanguageServer(textDocumentService, workspaceManager, workspaceService, configurator)

class Configurator(aiCompletionEngine: AICompletionEngineImpl, modelClient: ModelClient, schemaReader: SchemaReaderImpl):
  def configureApp(lspConfig: SDLBContext): Unit =
    def setting(key: String): Option[String] = Try(lspConfig.rootConfig.getString(key)).toOption.map(_.trim).filter(_.nonEmpty)
    setting("logLevel").foreach(LoggingManager.setRootLevel)
    aiCompletionEngine.tabStopsPrompt = Try(lspConfig.rootConfig.getString("tabStopsPrompt")).toOption
    aiCompletionEngine.model = setting("aiModel")
    modelClient match
      case client: OpenAICompatibleClient => client.configure(setting("aiBaseUrl"))
      case _ => ()
    // Relative to the project root, i.e. the parent of the .sdlb folder of the LSP config file
    val projectRoot = Try(Paths.get(URI(lspConfig.textContext.uri)).getParent.getParent).toOption
    schemaReader.useSchemaFile(setting("schemaPath").flatMap(path => projectRoot.map(_.resolve(path).normalize())))

