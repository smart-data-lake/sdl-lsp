# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Language Server (LSP) for [Smart Data Lake Builder](https://smartdatalake.ch/) HOCON `.conf` configuration files, written in Scala 3 on top of LSP4J. It provides schema-driven completion (with optional Gemini-powered tab-stop defaults), hover descriptions, and multi-file awareness. It talks to the client over stdin/stdout via JSON-RPC.

## Build & test

Maven, Java 17 (set via `java.version` in the pom; scalac gets `-release 17`), Scala 3.3.x.

```bash
mvn clean package            # builds target/sdl-lsp-1.0-SNAPSHOT-jar-with-dependencies.jar (runs tests)
mvn package -DskipTests      # build without tests
mvn test                     # run all tests (this is what CI runs)
mvn package -Drevision=1.2.0 # build a given version (the pom version is ${revision}, default 1.0-SNAPSHOT)
mvn test -Dsuites=io.smartdatalake.completion.SDLBCompletionEngineSpec          # single suite
mvn test -Dsuites='io.smartdatalake.completion.SDLBCompletionEngineSpec @partial test name'  # tests whose name contains the text after @
```

Releases: pushing a tag `vX.Y.Z` runs `.github/workflows/release.yml`, which builds with `-Drevision=X.Y.Z` and creates a GitHub release with the fat jar (as `sdl-lsp-X.Y.Z.jar` and `sdl-lsp.jar`); a suffix like `-rc1` makes it a pre-release. There is no Maven repository publishing.

Surefire is disabled; tests run via `scalatest-maven-plugin`, so use `-Dsuites`, not `-Dtest`. Run the server with `java -jar <jar>`. The client type (IntelliJ/LSP4IJ, VS Code, …) is detected from `clientInfo` in the initialize request (`ClientType.fromClientName`); `--client intellij|vscode` overrides it.

## Architecture

**Wiring / DI.** `modules/AppModule.scala` is a hand-rolled cake-style module: every component is a (mostly `lazy`) val there, and `Main` extends it. Tests extend `UnitSpec`, which mixes in `TestModule` and overrides vals (e.g. the schema is loaded from `src/test/resources/fixture/sdl-schema/...` instead of the main resource). When adding a component, register it in `AppModule` and override in `TestModule` if tests need a concrete type.

**Stdout is sacred.** `Main` keeps a reference to the real `System.out` for JSON-RPC and redirects `System.out` to the logger (`LoggingManager`). Never print to stdout; use the `SDLBLogger` trait (`trace/debug/info/warn/error`). Logback (`src/main/resources/logback.xml`) writes to `~/.sdl-lsp/logs/sdl-lsp.log` (`SDL_LSP_LOG_DIR`) and to the LSP client via the `ClientLogger` appender, at INFO by default (`SDL_LSP_LOG_LEVEL`, or `logLevel` in the LSP config at runtime). Tests use `src/test/resources/logback-test.xml`, logging to `target/test-logs/`.

**Request flow.** `SmartDataLakeLanguageServer.initialize` → `WorkspaceManager.initialize(rootUri)` loads `.sdlb/lsp-config.conf` and the config files, then passes the LSP config to `Configurator` (which sets the custom AI prompt). `src/main/resources/lsp-config/default-config.conf` is only the template offered as completion when editing the LSP config file. `SmartDataLakeTextDocumentService` handles `completion`/`hover`/`didOpen`/`didChange`/... by looking up the `SDLBContext` for the URI, positioning it at the caret, and delegating to the engines. Many LSP methods are still `???` stubs.

**Workspaces.** `languageserver/workspace/WorkspaceManager` (injected into the text document service and the language server) owns all document state behind a lock. `configPaths` in `.sdlb/lsp-config.conf` (comma-separated or HOCON list, like the SDLB CLI `--config` option; default: whole project root) lists files and directories forming the single `Workspace`; directories are walked recursively for `.conf`, skipping hidden folders and `target`. Files opened outside these paths get an isolated context. `Workspace` is immutable: it caches the *last valid* parsed config of each file (`parsed`), contexts are built lazily and marked `stale` when another file changes, and refreshed on `getContext`. `didClose` reloads the file from disk (discarding unsaved edits) or removes it if deleted. Walked files are keyed by `normalizeURI` to match client URIs (Windows/WSL variants); membership of newly opened files is checked on `Path`s.

**Context model.** `context/SDLBContext` (caret-aware: `parentPath` + `word`) wraps `TextContext`: the file's text, the unresolved `othersConfig` of the other workspace files, and `rootConfig` = current file `withFallback` others, resolved with unresolved substitutions allowed (the current file wins on conflicts). Only the current file is parsed on a keystroke; while its text is invalid, the last valid `rootConfig` is kept. Files are parsed with their URI as origin description, so values know their file. `MultiLineTransformer` flattens `"""` multi-line strings into `configText`, which is only used to locate the caret; `computeCorrectedPosition` maps the client's caret position into it — position bugs usually live here or in `hocon/HoconParser` (`retrieveWordAtPosition`, `retrieveParentPath`). Line numbers from LSP are 0-based; the service adds 1 before calling `withCaretPosition`.

**Schema.** `schema/SchemaReaderImpl` reads the SDLB JSON schema (`src/main/resources/sdl-schema/sdl-schema-2.5.0.json`) with circe and navigates it along the context's `parentPath`, following `type` values to the concrete subtype. It returns either an `AttributeCollection` (attributes to suggest) or a `TemplateCollection` (whole object snippets with `${n:...}` tab stops). `SchemaContext` holds the navigation logic; `SchemaReaderImpl.navigate` walks it in strict mode (completion of attributes, hover) or best-effort mode (values of `type`, where the type being edited must not narrow the schema). Users can replace the bundled schema with `schemaPath` in the LSP config (`useSchemaFile`, applied by `Configurator`, relative to the project root). Upgrading the bundled schema means changing the path in `AppModule` (and the test fixture if needed).

**Completion.** `SDLBCompletionEngineImpl` merges schema suggestions with `ContextAdvisor` suggestions (e.g. existing dataObject IDs for `inputId`/`outputId(s)`). Results are post-processed by a client-specific `FormattingStrategy` (IntelliJ needs special handling). Typing in `.sdlb/lsp-config.conf` itself offers a "Default Template" completion instead.

**Navigation.** `navigation/NavigationEngine` implements definition, references and document symbols (outline; block ends found by `HoconScanner`, which skips strings and comments) on `ConfigFile`s (text + last valid parsed config) given by `WorkspaceManager.visibleFiles`. `context/References` maps reference attributes to sections (`inputId(s)`/`outputId(s)` → `dataObjects`, `connectionId` → `connections`) and is shared with `ContextAdvisorImpl`. Locations come from the config origins (each file is parsed with its URI as origin description), the column from searching the id in the line.

**Diagnostics.** `diagnostics/DiagnosticsEngine` reports HOCON syntax errors (only these while the file doesn't parse), references to ids defined in none of the visible files, and `type` values without a dot unknown to the schema (`SchemaReaderImpl.typeNames`; dotted names are custom classes). The text document service publishes them debounced on a single scheduler thread (open 200 ms, change 500 ms, save: all open documents), and clears them on close; it receives the `LanguageClient` through `LanguageClientAware.connect` from the language server.

**AI completion.** `OpenAICompatibleClient` (sttp + circe; circe is the only JSON library, also used for the schema) calls any OpenAI chat completions endpoint: `aiBaseUrl`/`aiModel` in the LSP config (applied by `Configurator`), default Gemini's OpenAI compatibility endpoint, key from `AI_API_KEY` or `GOOGLE_API_KEY`; enabled with a key or an explicit base URL. `AICompletionEngineImpl` rewrites tab-stop defaults of template snippets. Template items only carry a small `CompletionData` (`withTabStops`, `parentPath`) in `item.data`; the AI context (merged config rendered as concise JSON) is rendered once per completion request, only when AI is enabled, and remembered for `resolveCompletionItem`. The top 3 items are precomputed asynchronously into an `LruCache` keyed by insert text, parent path and context hash (failures are evicted), and awaited non-blockingly (3s timeout, fallback to the original text) in `resolveCompletionItem`. The default prompt lives only in `lsp-config/default-config.conf` and is overridable via `tabStopsPrompt`; the engine extracts the JSON array from the reply, so no provider-specific JSON mode is used. Tests use a stub `ModelClient` from `TestModule`. There are two executors: `serviceExecutionContext` for LSP requests and `ioExecutionContext` for AI calls.

## Conventions

- Scala 3 syntax, mixing brace and significant-indentation styles; match the surrounding file. Java↔Scala collection conversions go through `conversions/ScalaJavaConverterAPI` (`.toScala`, `.toJava`), including `Future` → `CompletableFuture`.
- Tests are ScalaTest `AnyFlatSpec` + `should.Matchers`; HOCON fixtures live in `src/test/resources/fixture/hocon/` and are loaded with `loadFile(...)`.
- Saving `.sdlb/lsp-config.conf` (`didSave`) calls `WorkspaceManager.reload()`: config files are reloaded from disk, opened documents keep their text, and `Configurator` is re-applied through the `onLSPConfigLoaded` listener.
