package io.smartdatalake.modules
import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.languageserver.SmartDataLakeLanguageServer
import io.smartdatalake.schema.{SchemaReader, SchemaReaderImpl}
import io.smartdatalake.completion.ai.AICompletionEngineImpl
import io.smartdatalake.languageserver.SmartDataLakeTextDocumentService

trait TestModule extends AppModule {
  
  override lazy val schemaReader: SchemaReaderImpl = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
  override lazy val completionEngine: SDLBCompletionEngineImpl = new SDLBCompletionEngineImpl(schemaReader, contextAdvisor)
  override val aiCompletionEngine: AICompletionEngineImpl = new AICompletionEngineImpl(modelClient)
  override lazy val textDocumentService: SmartDataLakeTextDocumentService = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, aiCompletionEngine)
  override lazy val languageServer: SmartDataLakeLanguageServer = new SmartDataLakeLanguageServer(textDocumentService, workspaceService, configurator)(using serviceExecutionContext)

}
