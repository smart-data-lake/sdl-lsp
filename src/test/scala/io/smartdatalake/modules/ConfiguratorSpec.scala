package io.smartdatalake.modules

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaCollections.AttributeCollection
import io.smartdatalake.schema.{SchemaReaderImpl, SchemaRepository}

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}

import java.nio.file.Files

class ConfiguratorSpec extends UnitSpec with Eventually:

    override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds))

    private val customSchema = """{"type": "object", "properties": {"myRoot": {"type": "string", "description": "custom"}}}"""

    private def rootAttributes(reader: SchemaReaderImpl): List[String] =
        reader.retrieveAttributeOrTemplateCollection(SDLBContext.fromText("")) match
            case AttributeCollection(attributes) => attributes.map(_.name).toList
            case _ => List.empty

    "Configurator" should "use the schema given by schemaPath, relative to the project root" in {
        val root = Files.createTempDirectory("sdl-lsp-configurator")
        Files.createDirectories(root.resolve(".sdlb"))
        Files.createDirectories(root.resolve("schema"))
        Files.writeString(root.resolve("schema/my-schema.json"), customSchema)
        val lspConfigUri = root.resolve(".sdlb/lsp-config.conf").toUri.toString
        val reader = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
        val configurator = new Configurator(reader, schemaRepository)(using ioExecutionContext)
        rootAttributes(reader) should contain ("actions")

        configurator.configureApp(SDLBContext.fromText(lspConfigUri, """schemaPath = "schema/my-schema.json""""))
        rootAttributes(reader) shouldBe List("myRoot")

        configurator.configureApp(SDLBContext.fromText(lspConfigUri, ""))
        rootAttributes(reader) should contain ("actions")
    }

    it should "keep the bundled schema if the schema file is invalid" in {
        val file = Files.createTempFile("invalid-schema", ".json")
        Files.writeString(file, "{ not json")
        val reader = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
        reader.useSchemaFile(Some(file))
        rootAttributes(reader) should contain ("actions")
    }

    "Configurator" should "use the schema of the SDLB version of sdlbVersion or of the pom, downloaded in the background" in {
        val root = Files.createTempDirectory("sdl-lsp-configurator")
        val lspConfigUri = root.resolve(".sdlb/lsp-config.conf").toUri.toString
        val repository = SchemaRepository(Files.createTempDirectory("sdl-lsp-schemas"), url => url match
            case u if u.endsWith("sdl-schema-2.8.1.json") => Right("""{"x-version": "2.8.1", "type": "object", "properties": {"fromDownload": {"type": "string"}}}""")
            case u if u.endsWith("sdl-schema-2.7.0.json") => Right("""{"version": "2.7.0", "type": "object", "properties": {"older": {"type": "string"}}}""")
            case u => Left(s"HTTP 404 for $u"))
        val reader = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
        val configurator = new Configurator(reader, repository)(using ioExecutionContext)

        // No version: the bundled schema
        configurator.configureApp(SDLBContext.fromText(lspConfigUri, ""))
        reader.schemaVersion shouldBe "2.5.0"

        configurator.configureApp(SDLBContext.fromText(lspConfigUri, "sdlbVersion = 2.8.1"))
        eventually { rootAttributes(reader) shouldBe List("fromDownload") }
        reader.schemaVersion shouldBe "2.8.1"

        Files.writeString(root.resolve("pom.xml"),
            "<project><parent><groupId>io.smartdatalake</groupId><artifactId>sdl-parent</artifactId><version>2.7.0</version></parent></project>")
        configurator.configureApp(SDLBContext.fromText(lspConfigUri, ""))
        eventually { reader.schemaVersion shouldBe "2.7.0" }

        // Already cached: used at once
        configurator.configureApp(SDLBContext.fromText(lspConfigUri, "sdlbVersion = 2.8.1"))
        reader.schemaVersion shouldBe "2.8.1"

        // The version of the bundled schema is not downloaded
        configurator.configureApp(SDLBContext.fromText(lspConfigUri, "sdlbVersion = 2.5.0"))
        reader.schemaVersion shouldBe "2.5.0"
    }

    it should "keep the bundled schema if no schema can be found" in {
        val lspConfigUri = Files.createTempDirectory("sdl-lsp-configurator").resolve(".sdlb/lsp-config.conf").toUri.toString
        val reader = new SchemaReaderImpl("fixture/sdl-schema/sdl-schema-2.5.0.json")
        val configurator = new Configurator(reader, schemaRepository)(using ioExecutionContext)
        configurator.configureApp(SDLBContext.fromText(lspConfigUri, "sdlbVersion = 9.9.9"))
        Thread.sleep(200)
        reader.schemaVersion shouldBe "2.5.0"
        configurator.configureApp(SDLBContext.fromText(lspConfigUri, "sdlbVersion = not-a-version"))
        reader.schemaVersion shouldBe "2.5.0"
    }

    "The bundled schema" should "be readable" in {
        val reader = new SchemaReaderImpl(SchemaReaderImpl.BUNDLED_SCHEMA)
        reader.bundledVersion shouldBe "3.0.0-SNAPSHOT"
        reader.typeNames("actions") should contain allOf ("CopyAction", "CustomDataFrameAction")
        reader.typeNames("dataObjects") should contain ("CsvFileDataObject")
        rootAttributes(reader) should contain allOf ("actions", "dataObjects", "connections", "global")
    }
