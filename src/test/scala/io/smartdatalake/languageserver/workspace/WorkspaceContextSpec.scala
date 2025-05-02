package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec

class WorkspaceContextSpec extends UnitSpec:
    "WorkspaceContext" should "normalize Windows paths correctly" in {
        val rootUri = "file:///C:/Users/user/Downloads/getting-started/getting-started/.sdlb/lsp-config.conf"
        val uri = "file:///C:/Users/user/Downloads/getting-started/getting-started/.sdlb/lsp-config.conf"
        val expectedUri = "file:///C:/Users/user/Downloads/getting-started/getting-started/.sdlb/lsp-config.conf"
        textDocumentService.normalizeURI(rootUri = rootUri, uri = uri) shouldBe expectedUri
    }

    it should "normalize WSL paths correctly" in {
        val rootUri = "file:////wsl.localhost/Ubuntu-22.04/home/user/projects/sdlb-lsp/sdlb-sample-project"
        val uri = "file://wsl.localhost/Ubuntu-22.04/home/user/projects/sdlb-lsp/sdlb-sample-project/.sdlb/lsp-config.conf"
        val expectedUri = "file:////wsl.localhost/Ubuntu-22.04/home/user/projects/sdlb-lsp/sdlb-sample-project/.sdlb/lsp-config.conf"
        textDocumentService.normalizeURI(rootUri = rootUri, uri = uri) shouldBe expectedUri
    }