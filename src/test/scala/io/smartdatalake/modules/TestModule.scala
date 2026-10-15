package io.smartdatalake.modules
import io.smartdatalake.completion.SDLBCompletionEngineImpl
import io.smartdatalake.languageserver.SmartDataLakeLanguageServer
import io.smartdatalake.schema.SchemaReaderImpl
import io.smartdatalake.completion.ai.AICompletionEngineImpl
import io.smartdatalake.languageserver.SmartDataLakeTextDocumentService
import io.smartdatalake.completion.ai.model.ModelClient

import scala.concurrent.Future

trait TestModule extends AppModule {
  
  override lazy val schemaReader: SchemaReaderImpl = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
  override lazy val completionEngine: SDLBCompletionEngineImpl = new SDLBCompletionEngineImpl(schemaReader, contextAdvisor)
  // Never call a real model in tests, even if GOOGLE_API_KEY is set
  override val modelClient: ModelClient = new ModelClient:
    override def isEnabled: Boolean = false
    override def completeAsync(prompt: String, model: Option[String]): Future[String] = Future.successful("")
  override val aiCompletionEngine: AICompletionEngineImpl = new AICompletionEngineImpl(modelClient)
  override lazy val textDocumentService: SmartDataLakeTextDocumentService = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, aiCompletionEngine, workspaceManager)
  override lazy val languageServer: SmartDataLakeLanguageServer = new SmartDataLakeLanguageServer(textDocumentService, workspaceManager, workspaceService, configurator)(using serviceExecutionContext)

}
