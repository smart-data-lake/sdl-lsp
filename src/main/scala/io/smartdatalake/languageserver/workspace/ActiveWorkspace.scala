package io.smartdatalake.languageserver.workspace

import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import scala.annotation.tailrec
import scala.util.Using
import java.nio.file.Files
import java.nio.file.Path
import scala.util.Try
import java.nio.file.Paths
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.logging.SDLBLogger

class ActiveWorkspace(workspacePrefixes: String) extends WorkspaceStrategy with SDLBLogger:

    override def retrieve(uri: String, workspaces: List[Workspace]): Workspace =
        val sortedWorkspaces = workspaces
            .toList.sortBy(ws => (-ws.name.count(_ == ','), -ws.name.count(_ == '/'), -ws.name.length))
        sortedWorkspaces.find(ws => ws.name.split(",").exists(uri.startsWith(_)))
            .getOrElse {
                warn(s"Workspace not found when using ActiveWorkspace with $uri")
                val contents = Map(uri -> "")
                Workspace(
                    uri,
                    Map(uri -> SDLBContext.fromText(uri, "", contents)),
                    contents)
            }


    override def groupByWorkspaces(rootUri: String, contents: Map[String, String]): Map[String, Map[String, String]] =
        val activeWorkspacePrefixes = workspacePrefixes.split(",")
            .map(_.trim())
            .map(mergePath(rootUri, _))
        val activeWorkspaceName = activeWorkspacePrefixes.mkString(",")
        val (active, inactive) = contents.partition((uri, content) => activeWorkspacePrefixes.exists(uri.startsWith(_)))
        Map(activeWorkspaceName -> active) ++
            inactive.map((uri, content) => uri -> Map(uri -> content))

    private[workspace] def mergePath(prefix: String, path: String): String =
        val cleanedPath = if path.startsWith("./") then path.drop(2) else path
        prefix.reverse.dropWhile(_ == '/').reverse + "/" + cleanedPath.dropWhile(_ == '/')