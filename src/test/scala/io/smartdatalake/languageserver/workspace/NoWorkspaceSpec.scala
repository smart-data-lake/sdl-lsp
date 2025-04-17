package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext


class NoWorkspaceSpec extends UnitSpec {
    val workspaceStrategy = NoWorkspace()
    val rootUri = "file://root/"
    val contents = Map(
        "file://root/conf/local/whatever.conf" -> "",
        "file://root/conf/airport.conf" -> "",
        "file://root/conf/whatever.conf" -> "",
        "file://root/conf/trainstation.conf" -> "",
        "file://root/conf/cloud-dev/airport.conf" -> ""
    )
    
    "No Workspace" should "return the same uri as the workspace name" in {
        val uri = "file://root/conf/local/whatever.conf"
        val result = workspaceStrategy.retrieve(uri, List.empty)
        result.name should be (uri)
    }
    
    it should "group all files in a different workspace" in {
        val result = workspaceStrategy.groupByWorkspaces(rootUri, contents)
        result.size should be (5)
        result.keys should contain ("file://root/conf/local/whatever.conf")
        result.keys should contain ("file://root/conf/airport.conf")
        result.keys should contain ("file://root/conf/whatever.conf")
        result.keys should contain ("file://root/conf/trainstation.conf")
        result.keys should contain ("file://root/conf/cloud-dev/airport.conf")
        result("file://root/conf/local/whatever.conf").size should be (1)
    }
}
