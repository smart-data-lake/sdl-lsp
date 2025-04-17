package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import scala.util.Using
import java.nio.file.Files
import scala.annotation.tailrec
import java.nio.file.Path
import scala.util.Try
import java.nio.file.Paths

/**
  * WorkspaceStrategy is a trait that defines the strategy for managing workspaces in the context of a language server.
  * See README.md for more details.
  */
trait WorkspaceStrategy:
    
    def retrieve(uri: String, workspaces: List[Workspace]): Workspace

    def groupByWorkspaces(rootUri: String, contents: Map[String, String]): Map[String, Map[String, String]]

    def buildWorkspaceMap(rootUri: String, contents: Map[String, String]): Map[String, Workspace] =
        groupByWorkspaces(rootUri, contents).flatMap { case (workspaceName, contents) =>
            val contexts = contents.map { case (uri, content) =>
                uri -> SDLBContext.fromText(uri, content, contents)
            }
            val ws = Workspace(workspaceName, contexts, contents)
            contexts.map { case (uri, _) =>
                uri -> ws
            }
        }

object WorkspaceStrategy:
    def apply(rootUri: String, workspaceType: String, workspaceParameters: String): WorkspaceStrategy = workspaceType.toLowerCase().trim() match
        case "rootworkspace" => RootWorkspace(rootUri, workspaceParameters)
        case "activeworkspace" => ActiveWorkspace(workspaceParameters)
        case "singleworkspace" => SingleWorkspace()
        case "noworkspace" => NoWorkspace()
        case _ => throw new IllegalArgumentException(s"Unknown workspace type: $workspaceType")