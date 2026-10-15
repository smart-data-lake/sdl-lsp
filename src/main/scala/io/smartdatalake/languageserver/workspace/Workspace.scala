package io.smartdatalake.languageserver.workspace

import com.typesafe.config.Config
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.navigation.ConfigFile

/**
  * Immutable view of all files of the workspace.
  * Each file sees the last valid content of all the other files, so a file currently being edited
  * with a syntax error doesn't break the context of the others.
  * Each file is parsed only once per change, and contexts are computed lazily: they are only (re)built when requested, see [[refreshed]].
  * Syntax trees are parsed on demand, once per text, and shared between the context of a file and its [[ConfigFile]].
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
        val parsed = SDLBContext.parse(uri, text)
        // An invalid text doesn't change what the other files see, except for a new file
        val othersChanged = parsed.isDefined || !files.contains(uri)
        val config = parsed.orElse(files.get(uri).map(_.config)).getOrElse(SDLBContext.EMPTY_CONFIG)
        // The config of the other files only changes if the context is stale
        val context = contexts.get(uri) match
            case Some(context) if !stale.contains(uri) => context.withText(text)
            case Some(context) => context.withOthersConfig(othersConfig(files, uri)).withText(text)
            case None => SDLBContext.fromText(uri, text, othersConfig(files, uri))
        val newStale = if othersChanged then (stale ++ contexts.keySet) - uri else stale - uri
        copy(files = files.updated(uri, ConfigFile(uri, text, config)(() => context.textContext.syntax)), contexts = contexts.updated(uri, context), stale = newStale)

    def remove(uri: String): Workspace =
        copy(files = files - uri, contexts = contexts - uri, stale = (stale ++ contexts.keySet) - uri)

    /** Ensures the context of the given uri exists and is up to date with the content of the other files */
    def refreshed(uri: String): Workspace = files.get(uri) match
        case None => this
        case Some(file) => contexts.get(uri) match
            case Some(_) if !stale.contains(uri) => this
            case Some(context) => copy(contexts = contexts.updated(uri, context.withOthersConfig(othersConfig(files, uri)).withText(file.text)), stale = stale - uri)
            case None =>
                val context = SDLBContext.fromText(uri, file.text, othersConfig(files, uri))
                copy(files = files.updated(uri, sharingSyntax(file, context)), contexts = contexts.updated(uri, context), stale = stale - uri)

    /** The file, taking its syntax tree from the context of the same text */
    private def sharingSyntax(file: ConfigFile, context: SDLBContext): ConfigFile =
        ConfigFile(file.uri, file.text, file.config)(() => context.textContext.syntax)

    /** Unresolved config of all files except the given one, in a deterministic order */
    private def othersConfig(files: Map[String, ConfigFile], uri: String): Config =
        files.removed(uri).toList.sortBy(_._1).map(_._2.config).foldLeft(SDLBContext.EMPTY_CONFIG)(_.withFallback(_))

object Workspace:
    def fromTexts(texts: Map[String, String]): Workspace =
        Workspace(texts.map((uri, text) => uri -> ConfigFile(uri, text, SDLBContext.parse(uri, text).getOrElse(SDLBContext.EMPTY_CONFIG))()))
