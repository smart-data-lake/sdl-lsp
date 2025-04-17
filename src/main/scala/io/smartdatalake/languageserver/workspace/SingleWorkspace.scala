package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext
import io.smartdatalake.logging.SDLBLogger

class SingleWorkspace extends WorkspaceStrategy with SDLBLogger:

  override def retrieve(uri: String, workspaces: List[Workspace]): Workspace =
    warn(s"RootURI as a single workspace should handle all case. Abnormal call to method 'retrieve' with $uri")
    val contents = Map(uri -> "")
    Workspace(
        uri,
        Map(uri -> SDLBContext.fromText(uri, "", contents)),
        contents)

  override def groupByWorkspaces(rootUri: String, contents: Map[String, String]): Map[String, Map[String, String]] =
    Map(rootUri -> contents)

