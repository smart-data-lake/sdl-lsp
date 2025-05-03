package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext


class ActiveWorkspaceSpec extends UnitSpec {
    val workspaceStrategy = ActiveWorkspace("conf/local,conf/airport.conf, conf/trainstation.conf")
    val activeWorkspaceName = "file://root/conf/local,file://root/conf/airport.conf,file://root/conf/trainstation.conf"
    val inactiveWorkspaceName = "file://root/"
    val workspaces = List(
        Workspace(activeWorkspaceName, Map("" -> SDLBContext.EMPTY_CONTEXT), Map("" -> "")),
        Workspace(inactiveWorkspaceName, Map("" -> SDLBContext.EMPTY_CONTEXT), Map("" -> ""))
    )

    "Active Workspace" should "find the active workspace in provided folder" in {
        val uri = "file://root/conf/local/whatever.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (activeWorkspaceName)
    }

    it should "find the active workspace in provided file" in {
        val uri = "file://root/conf/airport.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (activeWorkspaceName)
    }

    it should "find the inactive workspace if not matching prefixes" in {
        val uri = "file://root/conf/whatever.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (inactiveWorkspaceName)

        val uri2 = "file://root/conf/cloud-dev/airport.conf"
        val result2 = workspaceStrategy.retrieve(uri2, workspaces)
        result2.name should be (inactiveWorkspaceName)
    }

    it should "partition files in 1 active and 2 inactive workspaces correctly" in {
        val rootUri = "file://root/"
        val contents = Map(
            "file://root/conf/local/whatever.conf" -> "",
            "file://root/conf/airport.conf" -> "",
            "file://root/conf/whatever.conf" -> "",
            "file://root/conf/trainstation.conf" -> "",
            "file://root/conf/cloud-dev/airport.conf" -> ""
        )
        val result = workspaceStrategy.groupByWorkspaces(rootUri, contents)
        val inactive1 = "file://root/conf/whatever.conf"
        val inactive2 = "file://root/conf/cloud-dev/airport.conf"
        result.size should be (3)
        result.keys should contain (activeWorkspaceName)
        result.keys should contain (inactive1)
        result.keys should contain (inactive2)
        result(activeWorkspaceName).size should be (3)
        result(inactive1).size should be (1)
        result(inactive2).size should be (1)
        result(activeWorkspaceName).keys should contain ("file://root/conf/local/whatever.conf")
        result(activeWorkspaceName).keys should contain ("file://root/conf/airport.conf")
        result(activeWorkspaceName).keys should contain ("file://root/conf/trainstation.conf")
        result(inactive1).keys should contain ("file://root/conf/whatever.conf")
        result(inactive2).keys should contain ("file://root/conf/cloud-dev/airport.conf")
    }

    it should "merge paths correctly" in {
        val expected = "file://root/conf/local/whatever.conf"

        val prefix = "file://root/conf/local/"
        val path = "/whatever.conf"
        val result = workspaceStrategy.mergePath(prefix, path)
        result should be (expected)

        val prefix2 = "file://root/conf/local"
        val path2 = "/whatever.conf"
        val result2 = workspaceStrategy.mergePath(prefix2, path2)
        result2 should be (expected)

        val prefix3 = "file://root/conf/local/"
        val path3 = "whatever.conf"
        val result3 = workspaceStrategy.mergePath(prefix3, path3)
        result3 should be (expected)

        val prefix4 = "file://root/conf/local"
        val path4 = "whatever.conf"
        val result4 = workspaceStrategy.mergePath(prefix4, path4)
        result4 should be (expected)

        val prefix5 = "file://root/conf/local"
        val path5 = "./whatever.conf"
        val result5 = workspaceStrategy.mergePath(prefix5, path5)
        result5 should be (expected)

        val prefix6 = "file://root/conf/local/"
        val path6 = "./whatever.conf"
        val result6 = workspaceStrategy.mergePath(prefix6, path6)
        result6 should be (expected)

        val prefix7 = "file://root/conf/local"
        val path7 = ".hidden/whatever.conf"
        val result7 = workspaceStrategy.mergePath(prefix7, path7)
        result7 should be ("file://root/conf/local/.hidden/whatever.conf")
    }
}
