package io.smartdatalake.modules

import io.smartdatalake.completion.{SDLBCompletionEngine, SDLBCompletionEngineImpl}
import io.smartdatalake.hover.{SDLBHoverEngine, SDLBHoverEngineImpl}
import io.smartdatalake.languageserver.{SmartDataLakeLanguageServer, SmartDataLakeTextDocumentService, SmartDataLakeWorkspaceService}
import io.smartdatalake.schema.{SchemaReader, SchemaReaderImpl}
import org.eclipse.lsp4j.services.{LanguageClientAware, LanguageServer, TextDocumentService, WorkspaceService}

trait AppModule {
  lazy val schemaReader: SchemaReader = new SchemaReaderImpl("sdl-schema/sdl-schema-2.5.0.json")
  lazy val completionEngine: SDLBCompletionEngine = new SDLBCompletionEngineImpl(schemaReader)
  lazy val hoverEngine: SDLBHoverEngine = new SDLBHoverEngineImpl(schemaReader)
  lazy val textDocumentService: TextDocumentService = new SmartDataLakeTextDocumentService(completionEngine, hoverEngine)
  lazy val workspaceService: WorkspaceService = new SmartDataLakeWorkspaceService
  lazy val languageServer: LanguageServer & LanguageClientAware = new SmartDataLakeLanguageServer(textDocumentService, workspaceService)

}

