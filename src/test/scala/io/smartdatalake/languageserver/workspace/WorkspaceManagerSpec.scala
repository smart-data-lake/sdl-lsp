package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext

import java.nio.file.{Files, Path}

class WorkspaceManagerSpec extends UnitSpec:

    /**
      * project/
      * ├── .sdlb/lsp-config.conf  (optional)
      * ├── conf/a.conf, conf/nested/b.conf, conf/.hidden/hidden.conf
      * ├── env/local.conf, env/prod.conf
      * ├── target/build.conf
      * └── outside.conf
      */
    private def createProject(lspConfig: Option[String] = None): Path =
        val root = Files.createTempDirectory("sdl-lsp-test")
        def write(relativePath: String, text: String): Unit =
            val file = root.resolve(relativePath)
            Files.createDirectories(file.getParent)
            Files.writeString(file, text)
        write("conf/a.conf", "dataObjects { obj-a { type = CsvFileDataObject } }")
        write("conf/nested/b.conf", "dataObjects { obj-b { type = JsonFileDataObject } }")
        write("conf/.hidden/hidden.conf", "hidden = true")
        write("env/local.conf", "local = true")
        write("env/prod.conf", "prod = true")
        write("target/build.conf", "build = true")
        write("outside.conf", "outside = true")
        lspConfig.foreach(write(".sdlb/lsp-config.conf", _))
        root

    private def uri(root: Path, relativePath: String): String = root.resolve(relativePath).toUri.toString

    private def initializedManager(root: Path): WorkspaceManager =
        val manager = WorkspaceManager()
        manager.initialize(root.toUri.toString)
        manager

    "WorkspaceManager" should "use all config files of the project root by default, except hidden and build folders" in {
        val root = createProject()
        val manager = initializedManager(root)
        val config = manager.getContext(uri(root, "conf/a.conf")).rootConfig
        config.hasPath("dataObjects.obj-b") shouldBe true
        config.getBoolean("local") shouldBe true
        config.getBoolean("prod") shouldBe true
        config.getBoolean("outside") shouldBe true
        config.hasPath("hidden") shouldBe false
        config.hasPath("build") shouldBe false
    }

    it should "search directories recursively and take files as is with configPaths" in {
        val root = createProject(Some("""configPaths = "./conf, env/local.conf""""))
        val manager = initializedManager(root)
        val config = manager.getContext(uri(root, "conf/a.conf")).rootConfig
        config.hasPath("dataObjects.obj-a") shouldBe true
        config.hasPath("dataObjects.obj-b") shouldBe true
        config.getBoolean("local") shouldBe true
        config.hasPath("prod") shouldBe false
        config.hasPath("outside") shouldBe false
    }

    it should "accept configPaths as a HOCON list" in {
        val root = createProject(Some("""configPaths = [conf, "env/local.conf"]"""))
        val config = initializedManager(root).getContext(uri(root, "conf/nested/b.conf")).rootConfig
        config.hasPath("dataObjects.obj-a") shouldBe true
        config.getBoolean("local") shouldBe true
        config.hasPath("prod") shouldBe false
    }

    it should "map the legacy ActiveWorkspace parameters to configPaths" in {
        val root = createProject(Some("workspaceType = ActiveWorkspace\nworkspaceParameters = \"conf/nested,env/prod.conf\""))
        val config = initializedManager(root).getContext(uri(root, "conf/nested/b.conf")).rootConfig
        config.getBoolean("prod") shouldBe true
        config.hasPath("dataObjects.obj-a") shouldBe false
    }

    it should "handle files outside of configPaths in isolation" in {
        val root = createProject(Some("""configPaths = "conf""""))
        val manager = initializedManager(root)
        val outsideUri = uri(root, "env/prod.conf")
        manager.getContext(outsideUri) should be (SDLBContext.EMPTY_CONTEXT)

        manager.updateContent(outsideUri, "prod = false")
        val outsideConfig = manager.getContext(outsideUri).rootConfig
        outsideConfig.getBoolean("prod") shouldBe false
        outsideConfig.hasPath("dataObjects") shouldBe false
        manager.getContext(uri(root, "conf/a.conf")).rootConfig.hasPath("prod") shouldBe false
    }

    it should "add new files created inside configPaths to the workspace" in {
        val root = createProject(Some("""configPaths = "conf""""))
        val manager = initializedManager(root)
        val newUri = uri(root, "conf/nested/new.conf")
        manager.updateContent(newUri, "dataObjects { obj-new { type = CsvFileDataObject } }")
        manager.getContext(newUri).rootConfig.hasPath("dataObjects.obj-a") shouldBe true
        manager.getContext(uri(root, "conf/a.conf")).rootConfig.hasPath("dataObjects.obj-new") shouldBe true
    }

    it should "use the text sent by the client when opening a known file" in {
        val root = createProject()
        val manager = initializedManager(root)
        val aUri = uri(root, "conf/a.conf")
        manager.updateContent(aUri, "dataObjects { obj-a2 { type = CsvFileDataObject } }")
        manager.getContext(aUri).rootConfig.hasPath("dataObjects.obj-a2") shouldBe true
    }

    it should "keep the latest edits of every file when editing several files" in {
        val root = createProject()
        val manager = initializedManager(root)
        val (aUri, bUri) = (uri(root, "conf/a.conf"), uri(root, "conf/nested/b.conf"))
        manager.updateContent(aUri, "dataObjects { obj-a2 { type = CsvFileDataObject } }")
        manager.updateContent(bUri, "dataObjects { obj-b2 { type = CsvFileDataObject } }")
        val aConfig = manager.getContext(aUri).rootConfig
        aConfig.hasPath("dataObjects.obj-a2") shouldBe true
        aConfig.hasPath("dataObjects.obj-b2") shouldBe true
        manager.getContext(bUri).rootConfig.hasPath("dataObjects.obj-a2") shouldBe true
    }

    it should "discard unsaved changes on close and remove deleted files" in {
        val root = createProject()
        val manager = initializedManager(root)
        val (aUri, bUri) = (uri(root, "conf/a.conf"), uri(root, "conf/nested/b.conf"))
        manager.updateContent(aUri, "dataObjects { unsaved { type = CsvFileDataObject } }")
        manager.close(aUri)
        manager.getContext(bUri).rootConfig.hasPath("dataObjects.unsaved") shouldBe false
        manager.getContext(bUri).rootConfig.hasPath("dataObjects.obj-a") shouldBe true

        Files.delete(root.resolve("conf/a.conf"))
        manager.close(aUri)
        manager.getContext(bUri).rootConfig.hasPath("dataObjects.obj-a") shouldBe false
    }

    it should "not fail for unknown files or without project root" in {
        val manager = WorkspaceManager()
        manager.initialize("")
        manager.getContext("file:///unknown.conf") should be (SDLBContext.EMPTY_CONTEXT)
        manager.close("file:///unknown.conf")
        manager.updateContent("example.conf", "a = 1")
        manager.getContext("example.conf").rootConfig.getInt("a") should be (1)
    }

    it should "normalize Windows paths correctly" in {
        val rootUri = "file:///C:/Users/user/Downloads/getting-started/getting-started/.sdlb/lsp-config.conf"
        val uri = "file:///C:/Users/user/Downloads/getting-started/getting-started/.sdlb/lsp-config.conf"
        val expectedUri = "file:///C:/Users/user/Downloads/getting-started/getting-started/.sdlb/lsp-config.conf"
        WorkspaceManager.normalizeURI(rootUri = rootUri, uri = uri) shouldBe expectedUri
    }

    it should "normalize WSL paths correctly" in {
        val rootUri = "file:////wsl.localhost/Ubuntu-22.04/home/user/projects/sdlb-lsp/sdlb-sample-project"
        val uri = "file://wsl.localhost/Ubuntu-22.04/home/user/projects/sdlb-lsp/sdlb-sample-project/.sdlb/lsp-config.conf"
        val expectedUri = "file:////wsl.localhost/Ubuntu-22.04/home/user/projects/sdlb-lsp/sdlb-sample-project/.sdlb/lsp-config.conf"
        WorkspaceManager.normalizeURI(rootUri = rootUri, uri = uri) shouldBe expectedUri
    }
