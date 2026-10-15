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
mvn test -Dsuites=io.smartdatalake.completion.SDLBCompletionEngineSpec          # single suite
mvn test -Dsuites='io.smartdatalake.completion.SDLBCompletionEngineSpec @partial test name'  # tests whose name contains the text after @
```

Surefire is disabled; tests run via `scalatest-maven-plugin`, so use `-Dsuites`, not `-Dtest`. Run the server with `java -jar <jar> [--client intellij|vscode]`.

## Architecture

**Wiring / DI.** `modules/AppModule.scala` is a hand-rolled cake-style module: every component is a (mostly `lazy`) val there, and `Main` extends it. Tests extend `UnitSpec`, which mixes in `TestModule` and overrides vals (e.g. the schema is loaded from `src/test/resources/fixture/sdl-schema/...` instead of the main resource). When adding a component, register it in `AppModule` and override in `TestModule` if tests need a concrete type.

**Stdout is sacred.** `Main` keeps a reference to the real `System.out` for JSON-RPC and redirects `System.out` to the logger (`LoggingManager`). Never print to stdout; use the `SDLBLogger` trait (`trace/debug/info/warn/error`). Logback (`src/main/resources/logback.xml`) writes to `logs/sdl-lsp.log` and to the LSP client via the `ClientLogger` appender.

**Request flow.** `SmartDataLakeLanguageServer.initialize` → `WorkspaceManager.initialize(rootUri)` loads `.sdlb/lsp-config.conf` and the config files, then passes the LSP config to `Configurator` (which sets the custom AI prompt). `src/main/resources/lsp-config/default-config.conf` is only the template offered as completion when editing the LSP config file. `SmartDataLakeTextDocumentService` handles `completion`/`hover`/`didOpen`/`didChange`/... by looking up the `SDLBContext` for the URI, positioning it at the caret, and delegating to the engines. Many LSP methods are still `???` stubs.

**Workspaces.** `languageserver/workspace/WorkspaceManager` (injected into the text document service and the language server) owns all document state behind a lock. `configPaths` in `.sdlb/lsp-config.conf` (comma-separated or HOCON list, like the SDLB CLI `--config` option; default: whole project root) lists files and directories forming the single `Workspace`; directories are walked recursively for `.conf`, skipping hidden folders and `target`. Files opened outside these paths get an isolated context. `Workspace` is immutable: each file sees the *last valid* text of the others (`shared`), contexts are built lazily and marked `stale` when another file changes, and refreshed on `getContext`. `didClose` reloads the file from disk (discarding unsaved edits) or removes it if deleted. Walked files are keyed by `normalizeURI` to match client URIs (Windows/WSL variants); membership of newly opened files is checked on `Path`s.

**Context model.** `context/SDLBContext` (caret-aware: `parentPath` + `word`) wraps `TextContext` (immutable: original text, flattened config text, and the Typesafe `Config` parsed from *current file + other workspace files*, then `resolve()`d). `MultiLineTransformer` flattens `"""` multi-line strings before parsing, and `computeCorrectedPosition` maps the client's caret position into the flattened text — position bugs usually live here or in `hocon/HoconParser` (`retrieveWordAtPosition`, `retrieveParentPath`). Line numbers from LSP are 0-based; the service adds 1 before calling `withCaretPosition`.

**Schema.** `schema/SchemaReaderImpl` reads the SDLB JSON schema (`src/main/resources/sdl-schema/sdl-schema-2.5.0.json`) with ujson and navigates it along the context's `parentPath`, following `type` values to the concrete subtype. It returns either an `AttributeCollection` (attributes to suggest) or a `TemplateCollection` (whole object snippets with `${n:...}` tab stops). `SchemaContext` holds the navigation logic. Upgrading the schema version means changing the path in `AppModule` (and the test fixture if needed).

**Completion.** `SDLBCompletionEngineImpl` merges schema suggestions with `ContextAdvisor` suggestions (e.g. existing dataObject IDs for `inputId`/`outputId(s)`). Results are post-processed by a client-specific `FormattingStrategy` (IntelliJ needs special handling). Typing in `.sdlb/lsp-config.conf` itself offers a "Default Template" completion instead.

**AI completion.** If `GOOGLE_API_KEY` is set, `AICompletionEngineImpl` (via `GeminiClient`, sttp + circe) rewrites tab-stop defaults of template snippets. The top 3 items are precomputed asynchronously on `completion` (keyed by insert text, attached via `CompletionData` JSON in `item.data`) and awaited (3s timeout, fallback to original) in `resolveCompletionItem`. The prompt is overridable via `tabStopsPrompt` in the LSP config. There are two executors: `serviceExecutionContext` for LSP requests and `ioExecutionContext` for AI calls.

## Conventions

- Scala 3 syntax, mixing brace and significant-indentation styles; match the surrounding file. Java↔Scala collection conversions go through `conversions/ScalaJavaConverterAPI` (`.toScala`, `.toJava`), including `Future` → `CompletableFuture`.
- Tests are ScalaTest `AnyFlatSpec` + `should.Matchers`; HOCON fixtures live in `src/test/resources/fixture/hocon/` and are loaded with `loadFile(...)`.
- Changes to the LSP config file require a server restart on the client side.
