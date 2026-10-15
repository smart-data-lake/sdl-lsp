package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext

/**
  * Immutable view of all files of the workspace.
  * Each file sees the last valid content of all the other files, so a file currently being edited
  * with a syntax error doesn't break the context of the others.
  * Contexts are computed lazily: they are only (re)built when requested, see [[refreshed]].
  *
  * @param texts current text of each file
  * @param shared last valid text of each file, as seen by the other files
  * @param contexts cache of computed contexts
  * @param stale contexts that are not aware of the latest shared contents yet
  */
private[workspace] case class Workspace(
    texts: Map[String, String] = Map.empty,
    shared: Map[String, String] = Map.empty,
    contexts: Map[String, SDLBContext] = Map.empty,
    stale: Set[String] = Set.empty):

    def contains(uri: String): Boolean = texts.contains(uri)

    def updateContent(uri: String, text: String): Workspace =
        val newShared =
            if SDLBContext.isConfigValid(text) then shared.updated(uri, text)
            else if shared.contains(uri) then shared
            else shared.updated(uri, "")
        val context = contexts.get(uri)
            .map(_.withContents(newShared).withText(text))
            .getOrElse(SDLBContext.fromText(uri, text, newShared))
        val newStale = if newShared == shared then stale - uri else (stale ++ contexts.keySet) - uri
        copy(texts = texts.updated(uri, text), shared = newShared, contexts = contexts.updated(uri, context), stale = newStale)

    def remove(uri: String): Workspace =
        copy(texts = texts - uri, shared = shared - uri, contexts = contexts - uri, stale = (stale ++ contexts.keySet) - uri)

    /** Ensures the context of the given uri exists and is up to date with the shared contents */
    def refreshed(uri: String): Workspace = texts.get(uri) match
        case None => this
        case Some(text) => contexts.get(uri) match
            case Some(_) if !stale.contains(uri) => this
            case Some(context) => copy(contexts = contexts.updated(uri, context.withContents(shared).withText(text)), stale = stale - uri)
            case None => copy(contexts = contexts.updated(uri, SDLBContext.fromText(uri, text, shared)), stale = stale - uri)

object Workspace:
    def fromTexts(texts: Map[String, String]): Workspace =
        Workspace(texts, texts.map((uri, text) => uri -> (if SDLBContext.isConfigValid(text) then text else "")))
