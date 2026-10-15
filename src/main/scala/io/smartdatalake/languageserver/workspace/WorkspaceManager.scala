package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*
import io.smartdatalake.logging.SDLBLogger
import io.smartdatalake.navigation.ConfigFile

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, Paths, SimpleFileVisitor}
import scala.collection.mutable.ListBuffer
import scala.util.{Try, Using}

/**
  * Keeps track of the SDLB config files of the project.
  *
  * Like the `--config` option of the SDLB CLI, the LSP config key `configPaths` lists files and directories
  * (comma-separated, relative to the project root). Directories are searched recursively for `.conf` files.
  * All these files form a single workspace: each of them sees the content of the others.
  * Any other file opened by the client is handled in isolation. Without `configPaths`, the whole project root is used.
  *
  * Saving the LSP config reloads it, together with all config files, without losing unsaved changes of opened documents.
  *
  * Events sent by the client (IntelliJ):
  *   creating or opening a file triggers "didOpen"
  *   even with auto-save, only ctrl+S triggers "didSave"
  *   deleting or closing a file triggers "didClose"
  *   switching to a file already opened triggers nothing
  *   renaming a file from outside of watched files to watched files does NOT trigger "didOpen"
  */
class WorkspaceManager extends SDLBLogger:
    import WorkspaceManager.*

    private case class State(
        rootUri: String = "",
        configPaths: List[Path] = List.empty,
        workspace: Workspace = Workspace(),
        isolated: Map[String, Workspace] = Map.empty,
        lspConfig: SDLBContext = SDLBContext.EMPTY_CONTEXT,
        openDocuments: Map[String, String] = Map.empty):

        def isInWorkspace(uri: String): Boolean =
            workspace.contains(uri) || toPath(uri).exists(path => configPaths.exists(path.startsWith))

        def withContent(uri: String, text: String): State =
            if isInWorkspace(uri) then
                copy(workspace = workspace.updateContent(uri, text))
            else
                // An isolated file is a workspace of its own
                copy(isolated = isolated.updated(uri, isolated.getOrElse(uri, Workspace()).updateContent(uri, text)))

        /** The workspace the given file belongs to, if known */
        def workspaceOf(uri: String): Option[Workspace] =
            if workspace.contains(uri) then Some(workspace) else isolated.get(uri)

    private var state = State()
    private var lspConfigListeners: List[SDLBContext => Unit] = List.empty

    /** The listener is called each time the LSP config is loaded: at initialization and when it is saved */
    def onLSPConfigLoaded(listener: SDLBContext => Unit): Unit = synchronized {
        lspConfigListeners = lspConfigListeners :+ listener
    }

    def initialize(rootUri: String): SDLBContext =
        val newState = loadState(rootUri)
        synchronized { state = newState }
        notifyLSPConfigLoaded(newState.lspConfig)
        newState.lspConfig

    /** Reloads the LSP config and all config files from disk, keeping the current text of the opened documents */
    def reload(): Unit =
        val loadedState = loadState(synchronized(state.rootUri))
        val newState = synchronized {
            state = state.openDocuments.foldLeft(loadedState) { case (s, (uri, text)) => s.withContent(uri, text) }
                .copy(openDocuments = state.openDocuments)
            state
        }
        notifyLSPConfigLoaded(newState.lspConfig)

    private def loadState(rootUri: String): State =
        toPath(rootUri).filter(path => rootUri.nonEmpty && Files.isDirectory(path)) match
            case None =>
                info(s"No project root folder found with '$rootUri': each file is handled in isolation")
                State(rootUri)
            case Some(rootPath) =>
                val lspConfig = loadLSPConfig(rootUri, rootPath)
                val configPaths = configPathEntries(lspConfig) match
                    case Nil => List(rootPath)
                    case entries => entries.map(rootPath.resolve(_).normalize())
                configPaths.filterNot(Files.exists(_)).foreach(path => warn(s"Config path $path does not exist"))
                val texts = configPaths.flatMap(findConfigFiles).distinct.map { file =>
                    normalizeURI(rootUri, file.toUri.toString) -> Try(Files.readString(file)).getOrElse("")
                }.filterNot((uri, _) => isLSPConfigUri(uri)).toMap
                info(s"Loaded ${texts.size} config files from ${configPaths.mkString(", ")}")
                State(rootUri, configPaths, Workspace.fromTexts(texts), Map.empty, lspConfig)

    private def notifyLSPConfigLoaded(lspConfig: SDLBContext): Unit =
        synchronized(lspConfigListeners).foreach(listener =>
            Try(listener(lspConfig)).failed.foreach(ex => warn(s"Error while applying the LSP config: ${ex.getMessage}"))
        )

    def getContext(uri: String): SDLBContext =
        if isLSPConfigUri(uri) then SDLBContext.EMPTY_CONTEXT
        else synchronized {
            state = state.copy(workspace = state.workspace.refreshed(uri), isolated = state.isolated.updatedWith(uri)(_.map(_.refreshed(uri))))
            state.workspaceOf(uri).flatMap(_.contexts.get(uri))
                .getOrElse {
                    debug(s"No context known for $uri")
                    SDLBContext.EMPTY_CONTEXT
                }
        }

    /** To be called when a document is opened or changed */
    def updateContent(uri: String, text: String): Unit =
        if isLSPConfigUri(uri) then synchronized {
            state = state.copy(lspConfig = SDLBContext.fromText(uri, text))
        }
        else synchronized {
            state = state.withContent(uri, text).copy(openDocuments = state.openDocuments.updated(uri, text))
        }

    /** To be called when a document is saved: saving the LSP config applies it */
    def save(uri: String): Unit =
        if isLSPConfigUri(uri) then
            info(s"Reloading LSP config from $uri")
            reload()

    /** To be called when a document is closed: unsaved changes are discarded, deleted files are removed */
    def close(uri: String): Unit =
        if !isLSPConfigUri(uri) then
            val diskText = toPath(uri).filter(Files.isRegularFile(_)).flatMap(path => Try(Files.readString(path)).toOption)
            if diskText.isEmpty then info(s"Detecting deletion of $uri")
            synchronized {
                val updated = diskText match
                    case Some(text) => state.withContent(uri, text)
                    case None => state.copy(workspace = state.workspace.remove(uri), isolated = state.isolated - uri)
                state = updated.copy(openDocuments = state.openDocuments - uri)
            }

    /**
      * Files seen from the given document: all the workspace files, or only the document itself if it is outside of the workspace.
      * They are kept by the workspace, so their syntax trees are parsed only once per text
      */
    def visibleFiles(uri: String): List[ConfigFile] = synchronized {
        state.workspaceOf(uri).toList.flatMap(_.files.values)
    }

    def openDocuments: Set[String] = synchronized(state.openDocuments.keySet)

    def isLSPConfigUri(uri: String): Boolean = uri.endsWith(CONFIG_FILE_NAME)

    def defaultLSPConfigText: Option[String] =
        Option(getClass.getClassLoader.getResource("lsp-config/default-config.conf")).map { url =>
            Using.resource(url.openStream()) { inputStream => scala.io.Source.fromInputStream(inputStream).getLines().mkString("\n").trim }
        }

    /** Without LSP config file, an empty config at its place, so that the project root is known */
    private def loadLSPConfig(rootUri: String, rootPath: Path): SDLBContext =
        val file = rootPath.resolve(CONFIG_FILE_NAME)
        val uri = normalizeURI(rootUri, file.toUri.toString)
        Try(Files.readString(file)).toOption match
            case Some(text) =>
                info(s"Loading LSP config from $uri")
                SDLBContext.fromText(uri, text)
            case None =>
                info(s"No LSP config found at $file, using default settings")
                SDLBContext.fromText(uri, "")

    private[workspace] def configPathEntries(lspConfig: SDLBContext): List[String] =
        val config = lspConfig.rootConfig
        def readString(key: String): Option[String] = Try(config.getString(key)).toOption
        def readList(key: String): Option[List[String]] =
            Try(config.getStringList(key).toScala).toOption.orElse(readString(key).map(_.split(",").toList))

        val legacyEntries = readString("workspaceType").flatMap { workspaceType =>
            warn(s"workspaceType = $workspaceType is not supported anymore: please use configPaths instead")
            if workspaceType.trim.equalsIgnoreCase("ActiveWorkspace") then readList("workspaceParameters") else None
        }
        readList("configPaths").orElse(legacyEntries).getOrElse(List.empty).map(_.trim).filter(_.nonEmpty)


object WorkspaceManager:
    private val CONFIG_FILE_NAME = ".sdlb/lsp-config.conf"
    private val CONFIG_FILE_EXTENSION = ".conf"
    private val IGNORED_DIRECTORIES = Set("target", "node_modules")

    /** A file is taken as is, a directory is searched recursively for config files, skipping hidden and build directories */
    private[workspace] def findConfigFiles(start: Path): List[Path] =
        if Files.isRegularFile(start) then List(start)
        else if !Files.isDirectory(start) then List.empty
        else
            val files = ListBuffer.empty[Path]
            Files.walkFileTree(start, new SimpleFileVisitor[Path]:
                override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    val name = dir.getFileName.toString
                    if dir != start && (name.startsWith(".") || IGNORED_DIRECTORIES.contains(name)) then FileVisitResult.SKIP_SUBTREE
                    else FileVisitResult.CONTINUE

                override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if attrs.isRegularFile && file.toString.endsWith(CONFIG_FILE_EXTENSION) then files += file
                    FileVisitResult.CONTINUE

                override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            )
            files.toList

    private[workspace] def toPath(uri: String): Option[Path] =
        Try(Paths.get(new java.net.URI(uri))).orElse(Try(Paths.get(uri))).toOption.map(_.toAbsolutePath.normalize())

    private[workspace] def normalizeURI(rootUri: String, uri: String): String =
        val uriSplit = uri.split(":/", 2)
        val rootUriSplit = rootUri.split(":/", 2)
        if uriSplit.length <= 1 || rootUriSplit.length <= 1 then
            uri
        else
            val path = uriSplit(1).dropWhile(_ == '/')
            val rootProtocol = rootUriSplit(0) + ":/" + rootUriSplit(1).takeWhile(_ == '/')
            rootProtocol + path
