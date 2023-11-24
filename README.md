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

You can either [download](https://oss.sonatype.org/content/repositories/snapshots/io/smartdatalake/sdl-lsp/1.0-SNAPSHOT/) the jar and jump directly to [Setting a basic LSP client in Intellij](#Setting-a-basic-LSP-client-in-Intellij) or compile and generate the jar yourself.

To download it, go [here](https://oss.sonatype.org/content/repositories/snapshots/io/smartdatalake/sdl-lsp/1.0-SNAPSHOT/) and take the one finishing with `XXX-jar-with-dependencies.jar`.
You can now read [Setting a basic LSP client in Intellij](#Setting-a-basic-LSP-client-in-Intellij).

If you prefer build the jar, follow the instructions below.

### Dependencies
To be able to build an executable jar with this project, you will need:
* maven
* java 11 or newer
### Installing

* Clone the repo locally.
* run `mvn clean package`

### Setting a basic LSP client in Intellij

* Download the generic LSP support for Intellij [here](https://plugins.jetbrains.com/plugin/10209-lsp-support). Please note that if you are running an Intellij version later than 2022.X.X, you will be warned that the plugin is not compatible with your Intellij version, but you should be able to run it nevertheless. Please ignore this warning.
* Go to `plugins` in your Intellij IDE.
* Click on the gear aside `Marketplace` and `Installed` and click on `Install Plugin from disk...`. Add the downloaded plugin.
* Go to `Settings > Languages & Frameworks > Language Server Protocol > Server definitions`
* Choose `Raw command | Extension: conf | Command: java -jar ${path-to-project}/target/sdl-lsp-1.0-SNAPSHOT-jar-with-dependencies.jar`. Be sure you ran `mvn clean package` before.
Please adapt the name if you downloaded the jar.
* A red, yellow or green dot should have appeared in the bottom of your IDE. Red just means the LSP is inactive, yellow means the LSP is initializing and green means up and running. You can check timeouts, restart the server or check the connected files.


## Authors
[scalathe](https://github.com/dsalathe)

## License
This project is licensed under the GNU General Public License (GPL) version 3 - see the LICENSE.txt file for details
