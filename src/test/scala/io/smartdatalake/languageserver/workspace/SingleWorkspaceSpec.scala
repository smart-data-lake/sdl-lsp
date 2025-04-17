package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext


class SingleWorkspaceSpec extends UnitSpec {
    val workspaceStrategy = SingleWorkspace()
    val rootUri = "file://root/"
    val contents = Map(
        "file://root/conf/local/whatever.conf" -> "",
        "file://root/conf/airport.conf" -> "",
        "file://root/conf/whatever.conf" -> "",
        "file://root/conf/trainstation.conf" -> "",
        "file://root/conf/cloud-dev/airport.conf" -> ""
    )
    
    "Single Workspace" should "group all files in a single workspace" in {
        val result = workspaceStrategy.groupByWorkspaces(rootUri, contents)
        result.size should be (1)
        result.keys should contain (rootUri)
        result(rootUri).size should be (5)
        result(rootUri).keys should contain ("file://root/conf/local/whatever.conf")
    }
}
