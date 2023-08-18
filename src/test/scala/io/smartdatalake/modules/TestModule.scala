package io.smartdatalake.modules
import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.languageserver.SmartDataLakeLanguageServer
import io.smartdatalake.schema.{SchemaReader, SchemaReaderImpl}

trait TestModule extends AppModule {
  override lazy val schemaReader: SchemaReader = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
  override lazy val completionEngine: SDLBCompletionEngineImpl = new SDLBCompletionEngineImpl(schemaReader)
  override lazy val languageServer: SmartDataLakeLanguageServer = new SmartDataLakeLanguageServer(textDocumentService, workspaceService)

}
