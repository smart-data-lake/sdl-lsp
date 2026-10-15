package io.smartdatalake.languageserver.workspace

import com.typesafe.config.Config
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.navigation.ConfigFile

/**
  * Immutable view of all files of the workspace.
  * Each file sees the last valid content of all the other files, so a file currently being edited
  * with a syntax error doesn't break the context of the others.
  * Each file is parsed only once per change, and contexts are computed lazily: they are only (re)built when requested, see [[refreshed]].
  * Each version of a text is parsed once, and shared by the context of the file and its [[ConfigFile]]:
  * syntax trees and resolved configs are only computed when needed.
  *
  * @param files current text and last valid unresolved config of each file, as seen by the other files
  * @param contexts cache of computed contexts
  * @param stale contexts that are not aware of the latest content of the other files yet
  */
private[workspace] case class Workspace(
    files: Map[String, ConfigFile] = Map.empty,
    contexts: Map[String, SDLBContext] = Map.empty,
    stale: Set[String] = Set.empty):

    def contains(uri: String): Boolean = files.contains(uri)

    def updateContent(uri: String, text: String): Workspace =
        val file = files.get(uri).map(_.withText(text)).getOrElse(ConfigFile(uri, text))
        // An invalid text doesn't change what the other files see, except for a new file
        val othersChanged = file.source.config.isDefined || !files.contains(uri)
        // The config of the other files only changes if the context is stale
        val context = contexts.get(uri) match
            case Some(context) if !stale.contains(uri) => context.withSource(file.source)
            case Some(context) => context.withSource(file.source).withOthersConfig(othersConfig(files, uri))
            case None => SDLBContext.fromSource(file.source, othersConfig(files, uri))
        val newStale = if othersChanged then (stale ++ contexts.keySet) - uri else stale - uri
        copy(files = files.updated(uri, file), contexts = contexts.updated(uri, context), stale = newStale)

    def remove(uri: String): Workspace =
        copy(files = files - uri, contexts = contexts - uri, stale = (stale ++ contexts.keySet) - uri)

    /** Ensures the context of the given uri exists and is up to date with the content of the other files */
    def refreshed(uri: String): Workspace = files.get(uri) match
        case None => this
        case Some(file) => contexts.get(uri) match
            case Some(_) if !stale.contains(uri) => this
            case Some(context) => copy(contexts = contexts.updated(uri, context.withOthersConfig(othersConfig(files, uri))), stale = stale - uri)
            case None => copy(contexts = contexts.updated(uri, SDLBContext.fromSource(file.source, othersConfig(files, uri))), stale = stale - uri)

    /** Unresolved config of all files except the given one, in a deterministic order */
    private def othersConfig(files: Map[String, ConfigFile], uri: String): Config =
        files.removed(uri).toList.sortBy(_._1).map(_._2.config).foldLeft(SDLBContext.EMPTY_CONFIG)(_.withFallback(_))

object Workspace:
    def fromTexts(texts: Map[String, String]): Workspace =
        Workspace(texts.map((uri, text) => uri -> ConfigFile(uri, text)))
