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
        updated.files(uriB).text should be ("b = 20")
        updated.contexts(uriB).rootConfig.getInt("b") should be (20)
        updated.stale should be (Set(uriA, uriC))

        val refreshed = updated.refreshed(uriA)
        refreshed.contexts(uriA).rootConfig.getInt("b") should be (20)
        refreshed.stale should be (Set(uriC))
    }

    it should "share only the last valid content of a file with the other files" in {
        val updated = workspace.updateContent(uriB, "b = {").refreshed(uriA)
        updated.files(uriB).text should be ("b = {")
        updated.files(uriB).config.getInt("b") should be (2)
        updated.stale shouldBe empty
        updated.contexts(uriA).rootConfig.getInt("b") should be (2)
        updated.contexts(uriA).isConfigCompleted shouldBe true
    }

    it should "share an empty content for files invalid from the start" in {
        val ws = Workspace.fromTexts(Map(uriA -> "a = 1", uriB -> "b = {"))
        ws.files(uriB).config.isEmpty shouldBe true
        ws.refreshed(uriA).contexts(uriA).rootConfig.getInt("a") should be (1)
    }

    it should "remove a file and mark the others as stale" in {
        val updated = workspace.refreshed(uriA).refreshed(uriB).remove(uriB)
        updated.contains(uriB) shouldBe false
        updated.stale should be (Set(uriA))
        updated.refreshed(uriA).contexts(uriA).rootConfig.hasPath("b") shouldBe false
    }

    it should "resolve substitutions across files and give priority to the current file" in {
        val ws = Workspace.fromTexts(Map(
            uriA -> "basePath = /data\nfeed = from-a",
            uriB -> "dataObjects { obj { path = ${basePath}\"/obj\" } }\nfeed = from-b"
        ))
        val configB = ws.refreshed(uriB).contexts(uriB).rootConfig
        configB.getString("dataObjects.obj.path") should be ("/data/obj")
        configB.getString("feed") should be ("from-b")
        ws.refreshed(uriA).contexts(uriA).rootConfig.getString("feed") should be ("from-a")
    }

    it should "keep the config usable with unresolved substitutions" in {
        val ws = Workspace.fromTexts(Map(uriA -> "a = ${missing}\nb = 2")).refreshed(uriA)
        ws.contexts(uriA).rootConfig.getInt("b") should be (2)
    }

    it should "keep track of the file each value comes from" in {
        val ws = Workspace.fromTexts(Map(uriA -> "a = 1", uriB -> "b = 2")).refreshed(uriB)
        ws.contexts(uriB).rootConfig.getValue("a").origin().description() should include (uriA)
    }

    it should "parse the syntax tree of a file once, shared by its context" in {
        val updated = workspace.updateContent(uriB, "b = 21").refreshed(uriA).updateContent(uriA, "a = 11").refreshed(uriB)
        updated.files(uriB).syntax should be theSameInstanceAs updated.contexts(uriB).textContext.syntax
        // Files not edited keep their parsed tree
        updated.files(uriC) should be theSameInstanceAs workspace.files(uriC)
    }
}
