package io.smartdatalake.languageserver.workspace

import io.smartdatalake.context.SDLBContext
import io.smartdatalake.logging.SDLBLogger

case class Workspace(name: String, contexts: Map[String, SDLBContext], contents: Map[String, String]) extends SDLBLogger:
    def updateContent(uri: String, newContent: String): Workspace =
        val updatedContents = contents.updated(uri, newContent)
        // Update only active context: not all contexts of the workspace
        val updatedContext = contexts.get(uri)
            .map(_.withText(newContent))
            .getOrElse(SDLBContext.fromText(uri, newContent, updatedContents))
        val updatedContexts = contexts.updated(uri, updatedContext)
        copy(contexts = updatedContexts, contents = updatedContents)

    def withAllContentsUpdated: Workspace =
        // Update all context of the workspace
        val newContexts = contexts.map { case (uri, context) =>
            uri -> context.withContents(contents)
        }
        trace(s"Updating all contexts (${contexts.size}) of workspace $name")
        copy(contexts = newContexts)
