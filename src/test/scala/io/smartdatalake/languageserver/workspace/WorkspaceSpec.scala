package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec


class WorkspaceSpec extends UnitSpec {
    val baseUri = "file://root/conf/local"
    val (uriA, uriB, uriC) = (s"$baseUri/a.conf", s"$baseUri/b.conf", s"$baseUri/c.conf")
    val workspace = Workspace.fromTexts(Map(uriA -> "a = 1", uriB -> "b = 2", uriC -> "c = 3"))

    "Workspace" should "build contexts lazily, each seeing the other files" in {
        workspace.contexts shouldBe empty
        val refreshed = workspace.refreshed(uriB)
        refreshed.contexts.keySet should be (Set(uriB))
        val config = refreshed.contexts(uriB).rootConfig
        config.getInt("a") should be (1)
        config.getInt("b") should be (2)
        config.getInt("c") should be (3)
    }

    it should "update the edited context and mark the others as stale" in {
        val updated = workspace.refreshed(uriA).refreshed(uriC).updateContent(uriB, "b = 20")
        updated.texts(uriB) should be ("b = 20")
        updated.contexts(uriB).rootConfig.getInt("b") should be (20)
        updated.stale should be (Set(uriA, uriC))

        val refreshed = updated.refreshed(uriA)
        refreshed.contexts(uriA).rootConfig.getInt("b") should be (20)
        refreshed.stale should be (Set(uriC))
    }

    it should "share only the last valid content of a file with the other files" in {
        val updated = workspace.updateContent(uriB, "b = {").refreshed(uriA)
        updated.texts(uriB) should be ("b = {")
        updated.shared(uriB) should be ("b = 2")
        updated.stale shouldBe empty
        updated.contexts(uriA).rootConfig.getInt("b") should be (2)
        updated.contexts(uriA).isConfigCompleted shouldBe true
    }

    it should "share an empty content for files invalid from the start" in {
        val ws = Workspace.fromTexts(Map(uriA -> "a = 1", uriB -> "b = {"))
        ws.shared(uriB) should be ("")
        ws.refreshed(uriA).contexts(uriA).rootConfig.getInt("a") should be (1)
    }

    it should "remove a file and mark the others as stale" in {
        val updated = workspace.refreshed(uriA).refreshed(uriB).remove(uriB)
        updated.contains(uriB) shouldBe false
        updated.stale should be (Set(uriA))
        updated.refreshed(uriA).contexts(uriA).rootConfig.hasPath("b") shouldBe false
    }
}
