package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import java.nio.file.{Files, Path, Paths}
import scala.util.{Try, Using}
import java.io.File
import io.smartdatalake.logging.SDLBLogger
import scala.annotation.tailrec

/**
  * WorkspaceContext handles most scenarios of the workspace, but it also depends on the LSP client.
  * Events:
        creating a file triggers "didOpen"
        in Intellij, even with auto-save, only ctrl+S triggers "didSave"
        In Intellij, ctrl+S on a file without changes doesn't trigger "didSave" (good news for us)
        Switching to a file already opened triggers nothing
        opening a file triggers "didOpen"
        deleting a file triggers "didClose"
        closing a file triggers "didClose"
        renaming a file from outside of watched files to watched files DO NOT trigger "didOpen" with Intellij
  */
trait WorkspaceContext extends SDLBLogger:

    private val CONFIG_FILE_NAME = ".sdlb/lsp-config.conf"

    private var uriToWorkspace: Map[String, Workspace] = Map.empty
    private var lspConfig = SDLBContext.EMPTY_CONTEXT
    private var workspaceStrategy: WorkspaceStrategy = SingleWorkspace()

    def getContext(uri: String): SDLBContext =
        if isLSPConfigUri(uri) then
            SDLBContext.EMPTY_CONTEXT
        else
            uriToWorkspace(uri).contexts(uri)

    def insert(uri: String, text: String): Unit =
        if isLSPConfigUri(uri) then
            info(s"Inserting LSP config from $uri")
            lspConfig = SDLBContext.fromText(uri, text, Map.empty)
        else
            trace(s"Insertion: Checking context for $uri")
            if !uriToWorkspace.contains(uri) then
                val workspace = workspaceStrategy
                    .retrieve(uri, uriToWorkspace.values.toList)
                    .updateContent(uri, text)
                trace(s"New context detected: $workspace")
                uriToWorkspace = uriToWorkspace.updated(uri, workspace)
            else
                debug(s"Existing workspace ${uriToWorkspace(uri).name} for $uri")


    def update(uri: String, contentChanges: String): Unit =
        if isLSPConfigUri(uri) then
            info(s"Updating LSP config from $uri")
            lspConfig = lspConfig.withText(contentChanges)
        else
            trace(s"Updating context for $uri")
            val workspace = uriToWorkspace(uri)
            uriToWorkspace = uriToWorkspace.updated(uri, workspace.updateContent(uri, contentChanges))

    def updateWorkspace(uri: String): Unit =
        if !isLSPConfigUri(uri) then
            require(uriToWorkspace.contains(uri), s"URI $uri not found in workspaces")
            val workspace = uriToWorkspace(uri)
            val updatedWorkspace = workspace.withAllContentsUpdated
            uriToWorkspace = uriToWorkspace.map { case (uri, ws) => ws match
                case v if v.name == workspace.name => uri -> updatedWorkspace
                case v => uri -> v
            }


    def initializeWorkspaces(rootUri: String): SDLBContext =
        val rootPath = path(rootUri)

        if Files.exists(rootPath) && Files.isDirectory(rootPath) then
            val configFiles = Files.walk(rootPath)
                .filter(path => Files.isRegularFile(path) && path.toString.endsWith(".conf"))
                .toScala

            val (lspConfigContent, contents) = configFiles.map { file => normalizeURI(rootUri, file.toUri().toString()) ->
                Try {
                    val text = Files.readString(file)
                    if SDLBContext.isConfigValid(text) then
                        text
                    else
                        warn(s"Invalid config file: ${file.toUri().toString()}")
                        ""
                }.getOrElse("")
            }.toMap.partition((uri, _) => isLSPConfigUri(uri))

            lspConfig = loadLSPConfig(rootUri, lspConfigContent.headOption)
            
            val workspaceType = Try(lspConfig.rootConfig.getString("workspaceType"))
                .getOrElse("SingleWorkspace")
            val workspaceParameters = Try(lspConfig.rootConfig.getString("workspaceParameters"))
                .getOrElse("")

            workspaceStrategy = WorkspaceStrategy(rootUri, workspaceType, workspaceParameters)
            info(s"Using workspace strategy: $workspaceType with parameters: $workspaceParameters")

            info(s"loaded ${contents.size} config files from $rootUri")
            uriToWorkspace = workspaceStrategy.buildWorkspaceMap(rootUri, contents)
            debug(s"Initialized workspaces: ${uriToWorkspace.map((key, v) => key.toString + " -> " + v.contexts.size).mkString("\n", "\n", "")}")
        lspConfig

    def isUriDeleted(uri: String): Boolean = !Files.exists(path(uri))


    def isLSPConfigUri(uri: String): Boolean = uri.endsWith(CONFIG_FILE_NAME)

    def defaultLSPConfigText: Option[String] =
        val defaultConfig = Option(getClass.getClassLoader.getResource("lsp-config/default-config.conf"))
        defaultConfig.map(dc => Using.resource(dc.openStream()) { inputStream => scala.io.Source.fromInputStream(inputStream).getLines().mkString("\n").trim })

    private def loadLSPConfig(rootUri: String, lspConfigContent: Option[(String, String)]): SDLBContext = lspConfigContent match
        case Some((uri, text)) =>
            info(s"Loading LSP config from $uri")
            SDLBContext.fromText(uri, text, Map.empty)
        case None =>
            // default case: read default config
            defaultLSPConfigText match
                case Some(lspConfigText) =>
                    info(s"Loading default LSP config")
                    SDLBContext.fromText(
                        normalizeURI(rootUri, CONFIG_FILE_NAME),
                        lspConfigText,
                        Map.empty)
                case None =>
                    warn(s"No valid LSP config found")
                    SDLBContext.EMPTY_CONTEXT

    private def normalizeURI(rootUri: String, uri: String): String =
        val uriSplit = uri.split(":/")
        val rootUriSplit = rootUri.split(":/")
        if uriSplit.length <= 1 || rootUriSplit.length <= 1 then
            uri
        else
            val path = uriSplit(1).dropWhile(_ == '/')
            val rootProtocol = rootUriSplit(0) + ":/" + rootUriSplit(1).takeWhile(_ == '/')
            rootProtocol + path

    private def path(uri: String): Path = Try(Paths.get(new java.net.URI(uri))).getOrElse(Paths.get(uri))