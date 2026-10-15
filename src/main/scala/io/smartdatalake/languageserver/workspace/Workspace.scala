package io.smartdatalake.languageserver.workspace

import com.typesafe.config.Config
import io.smartdatalake.context.SDLBContext

/**
  * Immutable view of all files of the workspace.
  * Each file sees the last valid content of all the other files, so a file currently being edited
  * with a syntax error doesn't break the context of the others.
  * Each file is parsed only once per change, and contexts are computed lazily: they are only (re)built when requested, see [[refreshed]].
  *
  * @param texts current text of each file
  * @param parsed last valid unresolved config of each file, as seen by the other files
  * @param contexts cache of computed contexts
  * @param stale contexts that are not aware of the latest content of the other files yet
  */
private[workspace] case class Workspace(
    texts: Map[String, String] = Map.empty,
    parsed: Map[String, Config] = Map.empty,
    contexts: Map[String, SDLBContext] = Map.empty,
    stale: Set[String] = Set.empty):

    def contains(uri: String): Boolean = texts.contains(uri)

    def updateContent(uri: String, text: String): Workspace =
        val newParsed = SDLBContext.parse(uri, text) match
            case Some(config) => parsed.updated(uri, config)
            case None => if parsed.contains(uri) then parsed else parsed.updated(uri, SDLBContext.EMPTY_CONFIG)
        // The config of the other files only changes if the context is stale
        val context = contexts.get(uri) match
            case Some(context) if !stale.contains(uri) => context.withText(text)
            case Some(context) => context.withOthersConfig(othersConfig(newParsed, uri)).withText(text)
            case None => SDLBContext.fromText(uri, text, othersConfig(newParsed, uri))
        // An invalid text doesn't change what the other files see
        val newStale = if newParsed eq parsed then stale - uri else (stale ++ contexts.keySet) - uri
        copy(texts = texts.updated(uri, text), parsed = newParsed, contexts = contexts.updated(uri, context), stale = newStale)

    def remove(uri: String): Workspace =
        copy(texts = texts - uri, parsed = parsed - uri, contexts = contexts - uri, stale = (stale ++ contexts.keySet) - uri)

    /** Ensures the context of the given uri exists and is up to date with the content of the other files */
    def refreshed(uri: String): Workspace = texts.get(uri) match
        case None => this
        case Some(text) => contexts.get(uri) match
            case Some(_) if !stale.contains(uri) => this
            case Some(context) => copy(contexts = contexts.updated(uri, context.withOthersConfig(othersConfig(parsed, uri)).withText(text)), stale = stale - uri)
            case None => copy(contexts = contexts.updated(uri, SDLBContext.fromText(uri, text, othersConfig(parsed, uri))), stale = stale - uri)

    /** Unresolved config of all files except the given one, in a deterministic order */
    private def othersConfig(parsed: Map[String, Config], uri: String): Config =
        parsed.removed(uri).toList.sortBy(_._1).map(_._2).foldLeft(SDLBContext.EMPTY_CONFIG)(_.withFallback(_))

object Workspace:
    def fromTexts(texts: Map[String, String]): Workspace =
        Workspace(texts, texts.map((uri, text) => uri -> SDLBContext.parse(uri, text).getOrElse(SDLBContext.EMPTY_CONFIG)))
