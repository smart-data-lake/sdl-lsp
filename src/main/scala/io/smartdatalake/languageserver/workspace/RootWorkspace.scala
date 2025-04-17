package io.smartdatalake.languageserver.workspace

import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import scala.annotation.tailrec
import scala.util.Using
import java.nio.file.Files
import java.nio.file.Path
import scala.util.Try
import java.nio.file.Paths
import io.smartdatalake.context.SDLBContext

class RootWorkspace(rootUri: String, workspaceRoot: String) extends WorkspaceStrategy:

    override def retrieve(uri: String, workspaces: List[Workspace]): Workspace =
        val sortedWorkspaces = workspaces
            .toList.sortBy(ws => (-ws.name.count(_ == '/'), -ws.name.length))
        sortedWorkspaces.find(ws => uri.startsWith(ws.name))
            .getOrElse {
                val newWorkspaceNames = fetchWorkspaceNames(rootUri)
                val newWorkspaceName = newWorkspaceNames.find(uri.startsWith(_))
                    .getOrElse(rootUri)
                val contents = Map(uri -> "")
                Workspace(
                    newWorkspaceName,
                    Map(uri -> SDLBContext.fromText(uri, "", contents)),
                    contents)
            }


    override def groupByWorkspaces(rootUri: String, contents: Map[String, String]): Map[String, Map[String, String]] =
        val workspaceNames = fetchWorkspaceNames(rootUri)
        val sortedWorkspaces = workspaceNames.sortBy(path => (-path.count(_ == '/'), -path.length))
        val addedURIs = Set.empty[String]

        @tailrec
        def addWorkspace(remainingWorkspaces: List[String], addedURIs: Set[String],acc: Map[String, Map[String, String]]): Map[String, Map[String, String]] = remainingWorkspaces match
            case Nil => acc
            case workspaceName :: tail =>
                val filteredContents = contents.filter { case (uri, _) =>
                    uri.startsWith(workspaceName) && !addedURIs.contains(uri)
                }
                val newAddedURIs = addedURIs ++ filteredContents.keys.toSet
                val newAcc = acc.updated(workspaceName, filteredContents)
                addWorkspace(tail, newAddedURIs, newAcc)
            
        addWorkspace(sortedWorkspaces, addedURIs, Map.empty)

    private def fetchWorkspaceNames(rootUri: String): List[String] =
        val rootWorkspaces = rootUri + "/" + workspaceRoot
        val workspaces = Using(Files.newDirectoryStream(path(rootWorkspaces))) { stream =>
            stream.toScala
                .filter(Files.isDirectory(_))
                .map(rootWorkspaces + "/" + _.getFileName.toString)
                .toList
        }.getOrElse(List.empty)
        rootUri :: rootWorkspaces :: workspaces

    private def path(uri: String): Path = Try(Paths.get(new java.net.URI(uri))).getOrElse(Paths.get(uri))

