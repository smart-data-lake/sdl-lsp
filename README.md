# Smart Data Lake Language Server Protocol (SDL LSP)

[LSP Implementation](https://microsoft.github.io/language-server-protocol/implementors/servers/) for the [Smart Data Lake Builder](https://smartdatalake.ch/) configuration files.

## Description

This LSP server currently provides:

* smart autocompletion suggestions
* Hovering description of objects, their attributes etc.

## How it works

The server communicate with an LSP client, which can be a plugin in a code editor like Intellij, VSCode, Atom etc. or even a web interface.

Standard Input/Output channels are used to communicate with its client, using [JSON-RPC](https://www.jsonrpc.org/specification) protocol.

Usually the client has responsibility to start the server. They first communicate their capabilities and then the client make requests or provide notifications to the server.

## Getting Started

You can either [download](https://oss.sonatype.org/content/repositories/snapshots/io/smartdatalake/sdl-lsp/1.0-SNAPSHOT/) the jar and jump directly to [Setting a basic LSP client in Intellij](#setting-a-basic-lsp-client-in-intellij) or compile and generate the jar yourself.

To download it, go [here](https://oss.sonatype.org/content/repositories/snapshots/io/smartdatalake/sdl-lsp/1.0-SNAPSHOT/) and take the one finishing with `XXX-jar-with-dependencies.jar`.

You can now read [Setting a basic LSP client in Intellij](#setting-a-basic-lsp-client-in-intellij).

If you prefer build the jar, follow the instructions below.

### Dependencies

To be able to build an executable jar with this project, you will need:

* maven
* java 11 or newer

### Installing

* Clone the repo locally.
* run `mvn clean package`

### Setting a basic LSP client in Intellij

* Download the generic LSP support for Intellij using [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij).
* Look for the LSP4iJ icon on your IDE and click on the right and select `New Language Server`. See screnshot below: ![screenshot of Intellij for setting up LSP4IJ](resources/img/lsp4ij-plugin.png)
* provide the following parameters:
  * `Server > Name` -> `SDLB Server`
  * `Server > Command` -> `java -jar $PATH_TO_JAR` (see [Getting Started](#getting-started))
  * `Mappings > File name patters` -> click on `+` and then:
    * `File name patterns` -> `*.conf`
    * `Language Id` -> `hocon`
* Click on `ok`. The server should start.

### Setting the custom LSP client in VSCode

Work in progress, should be released soon. Stay tuned!

## Authors

[scalathe](https://github.com/dsalathe)

## License

This project is licensed under the GNU General Public License (GPL) version 3 - see the LICENSE.txt file for details
