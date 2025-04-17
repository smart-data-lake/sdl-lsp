package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext

class NoWorkspace extends WorkspaceStrategy:

  override def retrieve(uri: String, workspaces: List[Workspace]): Workspace =
    val contents = Map(uri -> "")
    Workspace(
        uri,
        Map(uri -> SDLBContext.fromText(uri, "", contents)),
        contents)

  override def groupByWorkspaces(rootUri: String, contents: Map[String, String]): Map[String, Map[String, String]] =
    contents.map((uri, content) => uri -> Map(uri -> content))

