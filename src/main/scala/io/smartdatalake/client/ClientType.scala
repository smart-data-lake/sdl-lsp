package io.smartdatalake.client

enum ClientType:
  case VSCode
  case IntelliJ
  case Unknown

object ClientType:
  private val vsCodeNames = List("visual studio code", "vscode", "vscodium", "cursor", "windsurf")
  // LSP4IJ runs in all JetBrains IDEs, which report their product name
  private val jetBrainsNames = List("intellij", "pycharm", "webstorm", "goland", "rider", "clion", "datagrip",
    "dataspell", "phpstorm", "rubymine", "rustrover", "android studio", "aqua", "fleet", "jetbrains")

  /** Client type from the client name sent in the initialize request */
  def fromClientName(name: String): ClientType =
    val lowerCaseName = name.toLowerCase
    if vsCodeNames.exists(lowerCaseName.contains) then VSCode
    else if jetBrainsNames.exists(lowerCaseName.contains) then IntelliJ
    else Unknown
