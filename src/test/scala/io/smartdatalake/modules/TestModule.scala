package io.smartdatalake.modules
import io.smartdatalake.completion.SDLBCompletionEngineImpl
import io.smartdatalake.languageserver.SmartDataLakeLanguageServer
import io.smartdatalake.schema.{SchemaReaderImpl, SchemaRepository}
import io.smartdatalake.languageserver.SmartDataLakeTextDocumentService

trait TestModule extends AppModule {
  
  override lazy val schemaReader: SchemaReaderImpl = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
  // Never download schemas in tests
  override lazy val schemaRepository: SchemaRepository =
    new SchemaRepository(java.nio.file.Files.createTempDirectory("sdl-lsp-schemas"), fetch = url => Left(s"offline: $url"))
  override lazy val completionEngine: SDLBCompletionEngineImpl = new SDLBCompletionEngineImpl(schemaReader, contextAdvisor)
  override lazy val textDocumentService: SmartDataLakeTextDocumentService = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine, workspaceManager, navigationEngine, diagnosticsEngine)
  override lazy val languageServer: SmartDataLakeLanguageServer = new SmartDataLakeLanguageServer(textDocumentService, workspaceManager, workspaceService, configurator)(using serviceExecutionContext)

}
