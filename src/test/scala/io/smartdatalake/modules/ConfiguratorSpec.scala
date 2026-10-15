package io.smartdatalake.modules

import io.smartdatalake.UnitSpec
import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaCollections.AttributeCollection
import io.smartdatalake.schema.SchemaReaderImpl

import java.nio.file.Files

class ConfiguratorSpec extends UnitSpec:

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
        val configurator = new Configurator(aiCompletionEngine, modelClient, reader)
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
