package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext


class WorkspaceSpec extends UnitSpec {
    val baseUri = "file://root/conf/local"
    val workspace = Workspace(baseUri,
        Map(
            s"$baseUri/a.conf" -> SDLBContext.EMPTY_CONTEXT,
            s"$baseUri/b.conf" -> SDLBContext.EMPTY_CONTEXT,
            s"$baseUri/c.conf" -> SDLBContext.EMPTY_CONTEXT),
        Map(
            s"$baseUri/a.conf" -> "",
            s"$baseUri/b.conf" -> "",
            s"$baseUri/c.conf" -> "")
        )
    
    "Workspace" should "update only one context with updateContent" in {
        val updatedWorkspace = workspace.updateContent(s"$baseUri/b.conf", "hello = salut")
        updatedWorkspace.name should be (baseUri)
        updatedWorkspace.contexts.size should be (3)

        updatedWorkspace.contents(s"$baseUri/a.conf") should be (workspace.contents(s"$baseUri/a.conf"))
        updatedWorkspace.contents(s"$baseUri/b.conf") should be ("hello = salut")
        updatedWorkspace.contents(s"$baseUri/c.conf") should be (workspace.contents(s"$baseUri/c.conf"))

        updatedWorkspace.contexts(s"$baseUri/a.conf") should be (SDLBContext.EMPTY_CONTEXT)
        updatedWorkspace.contexts(s"$baseUri/b.conf") should not be (SDLBContext.EMPTY_CONTEXT)
        updatedWorkspace.contexts(s"$baseUri/c.conf") should be (SDLBContext.EMPTY_CONTEXT)
    }

    it should "update all contexts with withAllContentsUpdated" in {
        val updatedWorkspace = workspace.updateContent(s"$baseUri/b.conf", "hello = salut").withAllContentsUpdated
        updatedWorkspace.name should be (baseUri)
        updatedWorkspace.contexts.size should be (3)

        updatedWorkspace.contents(s"$baseUri/a.conf") should be (workspace.contents(s"$baseUri/a.conf"))
        updatedWorkspace.contents(s"$baseUri/b.conf") should be ("hello = salut")
        updatedWorkspace.contents(s"$baseUri/c.conf") should be (workspace.contents(s"$baseUri/c.conf"))

        updatedWorkspace.contexts(s"$baseUri/a.conf") should not be (SDLBContext.EMPTY_CONTEXT)
        updatedWorkspace.contexts(s"$baseUri/b.conf") should not be (SDLBContext.EMPTY_CONTEXT)
        updatedWorkspace.contexts(s"$baseUri/c.conf") should not be (SDLBContext.EMPTY_CONTEXT)
    }
}
