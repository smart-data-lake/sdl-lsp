package io.smartdatalake.languageserver.workspace

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext


class RootWorkspaceSpec extends UnitSpec {
    val rootUri = "file://root"
    val rootWsName = "conf"
    val rootWsUri = s"$rootUri/$rootWsName"
    val local = s"$rootWsUri/local"
    val cloudDev = s"$rootWsUri/cloud-dev"

    val workspaceStrategy = new RootWorkspace(rootUri, rootWsName) {
        override protected def fetchWorkspaceNames(rootUri: String): List[String] = List(rootUri, rootWsUri, local, cloudDev)
    }

    
    
    val contents = Map(
        s"$local/whatever.conf" -> "",
        s"$rootWsUri/airport.conf" -> "",
        s"$rootWsUri/whatever.conf" -> "",
        s"$rootWsUri/trainstation.conf" -> "",
        s"$cloudDev/airport.conf" -> "",
        s"$rootUri/outside/nested-outside.conf" -> "",
        s"$rootUri/directly-outside.conf" -> ""
    )

    val workspaces = List(
        Workspace(local, Map("" -> SDLBContext.EMPTY_CONTEXT), Map("" -> "")),
        Workspace(rootUri, Map("" -> SDLBContext.EMPTY_CONTEXT), Map("" -> "")),
        Workspace(cloudDev, Map("" -> SDLBContext.EMPTY_CONTEXT), Map("" -> "")),
    )
    
    "Root Workspace" should "return rootUri as workspaceName if not found" in {
        val uri = "file://not-found.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (rootUri)
    }

    it should "return rootUri as workspaceName if not found in the root workspace" in {
        val uri = "file://root/outside/nested-outside.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (rootUri)
    }

    it should "return the correct workspace if inside the root workspace" in {
        val uri = "file://root/conf/local/whatever.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (local)
    }

    it should "return the correct workspace if inside the root workspace with a different prefix" in {
        val uri = "file://root/conf/cloud-dev/airport.conf"
        val result = workspaceStrategy.retrieve(uri, workspaces)
        result.name should be (cloudDev)
    }

    it should "group all files in their respective workspace" in {
        val result = workspaceStrategy.groupByWorkspaces(rootUri, contents)
        result.size should be (4)
        result.keys should contain (local)
        result.keys should contain (rootUri)
        result.keys should contain (cloudDev)
        result.keys should contain (rootWsUri)
        result(local).size should be (1)
        result(rootUri).size should be (2)
        result(cloudDev).size should be (1)
        result(rootWsUri).size should be (3)
        result(local).keys should contain (s"$local/whatever.conf")
        result(rootWsUri).keys should contain (s"$rootWsUri/airport.conf")
        result(rootWsUri).keys should contain (s"$rootWsUri/whatever.conf")
        result(rootWsUri).keys should contain (s"$rootWsUri/trainstation.conf")
        result(cloudDev).keys should contain (s"$cloudDev/airport.conf")
        result(rootUri).keys should contain (s"$rootUri/outside/nested-outside.conf")
        result(rootUri).keys should contain (s"$rootUri/directly-outside.conf")
    }
}
