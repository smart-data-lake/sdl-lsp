package io.smartdatalake.languageserver

import io.smartdatalake.completion.SDLBCompletionEngineImpl
import io.smartdatalake.context.SDLBContext
import org.eclipse.lsp4j.jsonrpc.messages
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.{CodeAction, CodeActionParams, CodeLens, CodeLensParams, Command, CompletionItem, CompletionItemKind, CompletionList, CompletionParams, DefinitionParams, DidChangeTextDocumentParams, DidCloseTextDocumentParams, DidOpenTextDocumentParams, DidSaveTextDocumentParams, DocumentFormattingParams, DocumentHighlight, DocumentHighlightParams, DocumentOnTypeFormattingParams, DocumentRangeFormattingParams, DocumentSymbol, DocumentSymbolParams, Hover, HoverParams, InsertReplaceEdit, Location, LocationLink, Position, Range, ReferenceParams, RenameParams, SignatureHelp, SignatureHelpParams, SymbolInformation, TextDocumentPositionParams, TextEdit, WorkspaceEdit}

import java.util
import java.util.concurrent.CompletableFuture
import scala.io.Source
import scala.util.Using

class SmartDataLakeTextDocumentService extends TextDocumentService {

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] = {

    CompletableFuture.supplyAsync(() => {
      val completionItems = new util.ArrayList[CompletionItem]()

      val fixtureText = //TODO weird behavior with \"\"\"
        """actions {
          |
          |  join-departures-airports {
          |    type = CustomDataFrameAction
          |
          |    inputIds = [stg-departures, int-airports]
          |    transformer = {
          |      type = SQLDfsTransformer
          |      code = {
          |        btl-connected-airports = "select stg_departures.estdepartureairport, stg_departures.estarrivalairport,        airports.*         from stg_departures join int_airports airports on stg_departures.estArrivalAirport = airports.ident"
          |      }
          |    }
          |  }
          |
          |  compute-distances {
          |    type = CopyAction
          |
          |    code = {
          |      btl-departures-arrivals-airports = "select btl_connected_airports.estdepartureairport, btl_connected_airports.estarrivalairport,        btl_connected_airports.name as arr_name, btl_connected_airports.latitude_deg as arr_latitude_deg, btl_connected_airports.longitude_deg as arr_longitude_deg,        airports.name as dep_name, airports.latitude_deg as dep_latitude_deg, airports.longitude_deg as dep_longitude_deg           from btl_connected_airports join int_airports airports on btl_connected_airports.estdepartureairport = airports.ident"
          |    }
          |    metadata {
          |      feed = compute
          |    }
          |  }
          |
          |  download-airports  {
          |
          |    inputId = ext-airports
          |  }
          |
          |}
          |
          |dataObjects {
          |
          |
          |}""".stripMargin.trim
      val suggestions: List[CompletionItem] = new SDLBCompletionEngineImpl().generateCompletionItems(SDLBContext.createContext(fixtureText, params.getPosition.getLine+1, params.getPosition.getCharacter))
      suggestions.foreach(e => completionItems.add(e))

      messages.Either.forLeft(completionItems).asInstanceOf[messages.Either[util.List[CompletionItem], CompletionList]]
    })
  }

  override def didOpen(didOpenTextDocumentParams: DidOpenTextDocumentParams): Unit = ???

  override def didChange(didChangeTextDocumentParams: DidChangeTextDocumentParams): Unit = ???

  override def didClose(didCloseTextDocumentParams: DidCloseTextDocumentParams): Unit = ???

  override def didSave(didSaveTextDocumentParams: DidSaveTextDocumentParams): Unit = ???

  override def resolveCompletionItem(completionItem: CompletionItem): CompletableFuture[CompletionItem] = ???

  override def hover(params: HoverParams): CompletableFuture[Hover] = super.hover(params)

  override def signatureHelp(params: SignatureHelpParams): CompletableFuture[SignatureHelp] = super.signatureHelp(params)

  override def definition(params: DefinitionParams): CompletableFuture[messages.Either[util.List[_ <: Location], util.List[_ <: LocationLink]]] = super.definition(params)

  override def references(referenceParams: ReferenceParams): CompletableFuture[util.List[_ <: Location]] = ???

  override def documentHighlight(params: DocumentHighlightParams): CompletableFuture[util.List[_ <: DocumentHighlight]] = super.documentHighlight(params)

  override def documentSymbol(params: DocumentSymbolParams): CompletableFuture[util.List[messages.Either[SymbolInformation, DocumentSymbol]]] = super.documentSymbol(params)

  override def codeAction(params: CodeActionParams): CompletableFuture[util.List[messages.Either[Command, CodeAction]]] = super.codeAction(params)

  override def codeLens(codeLensParams: CodeLensParams): CompletableFuture[util.List[_ <: CodeLens]] = ???

  override def resolveCodeLens(codeLens: CodeLens): CompletableFuture[CodeLens] = ???

  override def formatting(documentFormattingParams: DocumentFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def rangeFormatting(documentRangeFormattingParams: DocumentRangeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def onTypeFormatting(documentOnTypeFormattingParams: DocumentOnTypeFormattingParams): CompletableFuture[util.List[_ <: TextEdit]] = ???

  override def rename(renameParams: RenameParams): CompletableFuture[WorkspaceEdit] = ???
}
