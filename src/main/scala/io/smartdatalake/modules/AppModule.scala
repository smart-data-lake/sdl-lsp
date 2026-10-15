package io.smartdatalake.modules

import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.context.{ContextAdvisor, ContextAdvisorImpl}
import io.smartdatalake.hover.{SDLBHoverEngine, SDLBHoverEngineImpl}
import io.smartdatalake.languageserver.{SmartDataLakeLanguageServer, SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.schema.{SchemaReaderImpl, SchemaRepository, SdlbVersion, SdlbVersionDetector}
import io.smartdatalake.client.ClientAware
import org.eclipse.lsp4j.services.{LanguageClientAware, LanguageServer, TextDocumentService, WorkspaceService}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import io.smartdatalake.completion.ai.model.{ModelClient, OpenAICompatibleClient}
import io.smartdatalake.completion.ai.AICompletionEngineImpl
import io.smartdatalake.languageserver.workspace.WorkspaceManager
import io.smartdatalake.navigation.NavigationEngine
import io.smartdatalake.diagnostics.DiagnosticsEngine
import io.smartdatalake.context.SDLBContext
import scala.util.Try
import java.net.URI
import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicInteger
import io.smartdatalake.logging.{LoggingManager, SDLBLogger}

trait AppModule:
  lazy val schemaReader: SchemaReaderImpl = new SchemaReaderImpl(SchemaReaderImpl.BUNDLED_SCHEMA)
  lazy val schemaRepository: SchemaRepository = new SchemaRepository(SchemaRepository.DEFAULT_CACHE_DIR)
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
  lazy val diagnosticsEngine: DiagnosticsEngine = new DiagnosticsEngine(schemaReader, navigationEngine)
  lazy val textDocumentService: TextDocumentService & LanguageClientAware & ClientAware = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, aiCompletionEngine, workspaceManager, navigationEngine, diagnosticsEngine)
  lazy val workspaceService: WorkspaceService = new SmartDataLakeWorkspaceService
  lazy val configurator: Configurator = new Configurator(aiCompletionEngine, modelClient, schemaReader, schemaRepository)(using ioExecutionContext)
  lazy val languageServer: LanguageServer & LanguageClientAware = new SmartDataLakeLanguageServer(textDocumentService, workspaceManager, workspaceService, configurator)

/**
  * Applies the LSP config. The schema is, by priority:
  * the file given by schemaPath, the schema of the SDLB version given by sdlbVersion or found in the pom.xml of the project, the bundled schema.
  * Schemas of other versions than the bundled one are taken from the cache of the repository, or downloaded in the background.
  */
class Configurator(aiCompletionEngine: AICompletionEngineImpl, modelClient: ModelClient, schemaReader: SchemaReaderImpl, schemaRepository: SchemaRepository)
                  (using ExecutionContext) extends SDLBLogger:
  // Only the schema requested by the last configuration may be applied
  private val schemaRequest = AtomicInteger()

  def configureApp(lspConfig: SDLBContext): Unit =
    def setting(key: String): Option[String] = Try(lspConfig.rootConfig.getString(key)).toOption.map(_.trim).filter(_.nonEmpty)
    setting("logLevel").foreach(LoggingManager.setRootLevel)
    aiCompletionEngine.tabStopsPrompt = Try(lspConfig.rootConfig.getString("tabStopsPrompt")).toOption
    aiCompletionEngine.model = setting("aiModel")
    modelClient match
      case client: OpenAICompatibleClient => client.configure(setting("aiBaseUrl"))
      case _ => ()
    // The parent of the .sdlb folder of the LSP config file
    val projectRoot = Try(Paths.get(URI(lspConfig.textContext.uri)).getParent.getParent).toOption
    val download = Try(lspConfig.rootConfig.getBoolean("schemaDownload")).getOrElse(true)
    configureSchema(setting("schemaPath"), setting("sdlbVersion"), projectRoot, download)

  private def configureSchema(schemaPath: Option[String], sdlbVersion: Option[String], projectRoot: Option[Path], download: Boolean): Unit =
    val request = schemaRequest.incrementAndGet()
    schemaPath match
      case Some(path) => schemaReader.useSchemaFile(projectRoot.map(_.resolve(path).normalize()), "schemaPath of the LSP config")
      case None =>
        val requested = sdlbVersion.map(_ -> "sdlbVersion of the LSP config")
          .orElse(projectRoot.flatMap(SdlbVersionDetector.detect).map((version, pom) => version -> s"version of $pom"))
        requested match
          case None => schemaReader.useBundledSchema("no SDLB version in sdlbVersion of the LSP config nor in the pom.xml of the project")
          case Some((text, origin)) => SdlbVersion.parse(text) match
            case None => schemaReader.useBundledSchema(s"invalid SDLB version '$text' in $origin")
            case Some(version) if version.value == schemaReader.bundledVersion => schemaReader.useBundledSchema(s"SDLB $version, $origin")
            case Some(version) => schemaRepository.cachedSchema(version) match
              case Some(file) => schemaReader.useSchemaFile(Some(file), s"SDLB $version, $origin")
              case None =>
                schemaReader.useBundledSchema(s"until the schema of SDLB $version, $origin, is available")
                Future(schemaRepository.schemaFor(version, download)).foreach { result =>
                  if schemaRequest.get == request then result match
                    case Right((found, file)) =>
                      val reason = if found == version then s"SDLB $version, $origin" else s"closest available schema to SDLB $version, $origin"
                      schemaReader.useSchemaFile(Some(file), reason)
                    case Left(error) => warn(s"No schema found for SDLB $version, keeping the bundled schema: $error")
                }
