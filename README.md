# Smart Data Lake Language Server Protocol (SDL LSP)

[LSP Implementation](https://microsoft.github.io/language-server-protocol/implementors/servers/) for the [Smart Data Lake Builder](https://smartdatalake.ch/) configuration files.

## Features

This LSP server currently provides:

* smart autocompletion suggestions
* Hovering description of objects, their attributes etc.
* Go to definition and find references of dataObjects and connections (`inputId`, `outputIds`, `connectionId`, ...) and of substitutions (`${templates.myTemplate}`), across files
* Outline of the dataObjects, actions and connections of a file
* Diagnostics: HOCON syntax errors, references to unknown dataObjects or connections, unknown built-in types, attributes unknown to the schema, and substitutions of undefined paths

## How it works

The server communicate with an LSP client, which can be a plugin in a code editor like Intellij, VSCode, Atom etc. or even a web interface.

Standard Input/Output channels are used to communicate with its client, using [JSON-RPC](https://www.jsonrpc.org/specification) protocol.

Usually the client has responsibility to start the server. They first communicate their capabilities and then the client make requests or provide notifications to the server.

## Getting Started

### Requirements

* Java 17 or newer
* IntelliJ or VSCode

### Setting a basic LSP client in IntelliJ

* Download the generic LSP support for IntelliJ using [LSP4IJ](https://plugins.jetbrains.com/plugin/23257-lsp4ij).
* Look for the LSP4iJ icon in the tool list on the right of IntelliJ. Then select `New Language Server` icon on the right.
* See screnshot below: ![screenshot of Intellij for setting up LSP4IJ](resources/img/lsp4ij-plugin.png)
* Then `choose template...` "Smart Data Lake Language Server".
* Click on `ok`. The server should start.

LSP4iJ plugin should automatically download `sdl-lsp.jar` from the [latest release](https://github.com/smart-data-lake/sdl-lsp/releases/latest) (direct link: [sdl-lsp.jar](https://github.com/smart-data-lake/sdl-lsp/releases/latest/download/sdl-lsp.jar)) and store it in `.sdlb` directory.
`.sdlb/sdl-lsp.jar` is the filename referenced in the template.

### Setting the custom LSP client in VSCode

VSCode needs a client extension to start the server. It is published with each release, next to the jar:

* Download `sdl-lsp-vscode.vsix` from the [latest release](https://github.com/smart-data-lake/sdl-lsp/releases/latest) (direct link: [sdl-lsp-vscode.vsix](https://github.com/smart-data-lake/sdl-lsp/releases/latest/download/sdl-lsp-vscode.vsix)) and install it with `code --install-extension sdl-lsp-vscode.vsix`, or in VSCode with `Extensions: Install from VSIX...` in the command palette.
* Open the root folder of your SDLB project. The server starts when a `.conf` file is opened; its logs are in the `Output` view, under `SDLB Language Server`.

Like the IntelliJ template, the extension uses `.sdlb/sdl-lsp.jar` in the project root. If this file is missing, the extension downloads the jar of its own release there, so the extension and the server have the same version. To update the server, install the new extension, delete `.sdlb/sdl-lsp.jar` and reload the window.

Settings:

* `sdlLsp.jarPath`: another location of the jar, relative to the project root or absolute (default: `.sdlb/sdl-lsp.jar`). It is downloaded there as well if missing.
* `sdlLsp.javaPath`: the Java executable, if `java` on the `PATH` is not Java 17 or newer.
* `sdlLsp.env`: environment variables of the server, e.g. `{ "SDL_LSP_LOG_LEVEL": "debug" }`.

The extension associates `.conf` files with the language `hocon`. If another extension claims `.conf` files, check that the language mode in the status bar is `HOCON`.

**Hint:** With Remote-WSL or another remote connection, the extension and the server run on the remote side: install the extension there, with Java; an absolute `sdlLsp.jarPath` must be a path of the remote machine.

The extension lives in the [`vscode`](vscode) folder. To try changes without installing it, open that folder in VSCode, run `npm install` and press `F5`: a new window starts with the extension loaded.
To build the `.vsix` yourself, run `npx @vscode/vsce package --skip-license` in that folder.

### Download and run standalone

If you want to use the LSP server without installing a client, 
* Download the LSP jar file from [latest release](https://github.com/smart-data-lake/sdl-lsp/releases/latest).
* Start it manually in a terminal:
  ```bash
  java -jar sdl-lsp.jar 
  ```

**Hint:** Should you prefer building the project on your own, follow the instructions further below.

## LSP Server Configuration

The SDL LSP server behavior can be customized by creating a configuration file at `.sdlb/lsp-config.conf` in your project root. This file allows you to define the workspace, the SDLB schema and the logging.

**Hint:** You can generate a default template of the config file using the LSP itself. See screnshot below: ![screenshot of triggering default template of the LSP config file in LSP4IJ](resources/img/default-template-lsp-config.png)

### Workspace Configuration

The workspace determines which files are processed together by the LSP server. This matters for features like auto-completion that need context from multiple files, e.g. suggesting the ids of dataObjects defined in another file.

Like the `--config` option of the SDLB command line, `configPaths` lists the files and directories forming the workspace, comma-separated and relative to the project root. Directories are searched recursively for `.conf` files.

```hocon
configPaths = "config,envConfig/local.conf"
```

**Example structure:**

```plaintext
project/
├── .sdlb/
│   └── lsp-config.conf
├── config/              # Part of the workspace, including all subfolders
│   ├── abc/
│   │   └── extract.conf
│   └── common.conf
└── envConfig/
    ├── local.conf       # Part of the workspace
    └── prod.conf        # Not in the workspace
```

In this example, `extract.conf`, `common.conf` and `local.conf` all see each other's content. Files outside `configPaths`, like `prod.conf`, can still be edited with completion and hovering, but are handled in isolation: they neither see nor affect the workspace files.

If `configPaths` is not set, all `.conf` files of the project are part of the workspace (hidden folders and `target` are skipped).

Changes of the LSP config file are applied when it is saved: the config files are reloaded, without losing the unsaved changes of opened files.

**Hint:** `configPaths` replaces the former `workspaceType` and `workspaceParameters` settings. A configuration using `workspaceType = ActiveWorkspace` is still understood: its `workspaceParameters` are used as `configPaths`.

### SDLB Schema

Completion, hovering and diagnostics are based on the JSON schema of SDLB configurations. The server uses the schema of the SDLB version of your project:

1. `schemaPath` in `.sdlb/lsp-config.conf`: a schema file, relative to the project root.
2. `sdlbVersion` in `.sdlb/lsp-config.conf`, e.g. `sdlbVersion = 2.8.1`.
3. The SDLB version of the `pom.xml` of the project root or of its modules: the version of the parent `io.smartdatalake:sdl-parent`, else of a dependency `io.smartdatalake:sdl-*`, with its `${...}` properties resolved.
4. The bundled schema, of SDLB 3.0.0-SNAPSHOT.

The schemas of other SDLB versions are downloaded from [smartdatalake.ch](https://smartdatalake.ch/json-schema-viewer/schemas/) in the background and cached in `~/.sdl-lsp/schemas`; snapshots are downloaded again after a day. Without a schema for the exact version, the closest lower released version is used. Set `schemaDownload = false` to only use the cached schemas.

The schema in use and the reason are logged at startup, e.g. `Using the schema of SDLB 2.8.1 from ~/.sdl-lsp/schemas/sdl-schema-2.8.1.json (SDLB 2.8.1, version of /project/pom.xml)`. The `pom.xml` is read at startup and when the LSP config is saved.

### Logging

Logs are sent to the LSP client and written to `~/.sdl-lsp/logs/sdl-lsp.log`. The default level is `info`; set `logLevel = debug` in your `.sdlb/lsp-config.conf` for more details. The environment variables `SDL_LSP_LOG_LEVEL` and `SDL_LSP_LOG_DIR` change the level and the directory at startup; with `SDL_LSP_LOG_LEVEL=trace`, all JSON-RPC messages are logged as well.

## Build

### Build Requirements

To be able to build this project, you will need:

* maven
* java 17 or newer

### Building

* Clone the repo locally.
* run `mvn clean package`

After a successfull build, the `sdl-lsp-1.0-SNAPSHOT-jar-with-dependencies.jar` is located in `./target` directory.

### Releasing

Releases are published on GitHub by the `Release` workflow when a version tag is pushed:

```bash
git tag v1.2.0
git push origin v1.2.0
```

The workflow builds and tests the project with the version taken from the tag (`mvn package -Drevision=1.2.0`), then creates the GitHub release with generated release notes and the jar attached. Tags with a suffix, like `v1.2.0-rc1`, are published as pre-releases.

## Release Notes

see https://github.com/smart-data-lake/sdl-lsp/releases

## Authors

[scalathe](https://github.com/dsalathe)
[zzeekk](https://github.com/zzeekk)

## License

This project is licensed under the GNU General Public License (GPL) version 3 - see the LICENSE.txt file for details
