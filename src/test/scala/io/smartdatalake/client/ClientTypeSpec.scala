package io.smartdatalake.client

import io.smartdatalake.UnitSpec

class ClientTypeSpec extends UnitSpec:

    "Client type" should "be detected from the client name" in {
        ClientType.fromClientName("IntelliJ IDEA") shouldBe ClientType.IntelliJ
        ClientType.fromClientName("PyCharm") shouldBe ClientType.IntelliJ
        ClientType.fromClientName("Visual Studio Code") shouldBe ClientType.VSCode
        ClientType.fromClientName("Cursor") shouldBe ClientType.VSCode
        ClientType.fromClientName("Neovim") shouldBe ClientType.Unknown
    }
