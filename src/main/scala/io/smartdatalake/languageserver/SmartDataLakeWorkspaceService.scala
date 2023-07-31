package io.smartdatalake.languageserver

import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.{DidChangeConfigurationParams, DidChangeWatchedFilesParams, SymbolInformation, WorkspaceSymbol, WorkspaceSymbolParams}

import java.util
import java.util.concurrent.CompletableFuture

class SmartDataLakeWorkspaceService extends WorkspaceService {
  override def didChangeConfiguration(didChangeConfigurationParams: DidChangeConfigurationParams): Unit = ???

  override def didChangeWatchedFiles(didChangeWatchedFilesParams: DidChangeWatchedFilesParams): Unit = ???

  override def symbol(params: WorkspaceSymbolParams): CompletableFuture[messages.Either[util.List[_ <: SymbolInformation], util.List[_ <: WorkspaceSymbol]]] = super.symbol(params)
}
