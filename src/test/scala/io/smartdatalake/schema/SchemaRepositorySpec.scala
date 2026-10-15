package io.smartdatalake.schema

import io.smartdatalake.UnitSpec
import org.scalatest.EitherValues

import java.nio.file.Files
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*

class SchemaRepositorySpec extends UnitSpec with EitherValues:

    private val baseUrl = "https://schemas/"
    private def schema(version: String) = s"""{"version": "$version", "type": "object"}"""
    private def version(text: String) = SdlbVersion.parse(text).value

    /** A repository serving the given files, recording the downloaded URLs */
    private class Fixture(files: Map[String, String], snapshotMaxAge: FiniteDuration = 1.day):
        val cacheDir = Files.createTempDirectory("sdl-lsp-schemas")
        val downloads = ListBuffer[String]()
        @volatile var online = true
        val repository = SchemaRepository(cacheDir, url => {
            downloads += url
            if online then files.get(url.stripPrefix(baseUrl)).toRight(s"HTTP 404 for $url") else Left("offline")
        }, baseUrl, snapshotMaxAge)
        def found(text: String, download: Boolean = true): Either[String, (String, String)] =
            repository.schemaFor(version(text), download).map((v, file) => v.value -> Files.readString(file))

    private val index = """["sdl-schema-2.7.0.json", "sdl-schema-2.8.0.json", "sdl-schema-2.8.1.json", "sdl-schema-2.9.1-SNAPSHOT.json"]"""

    "Schema repository" should "download a schema once and then use the cache" in {
        val fixture = Fixture(Map("sdl-schema-2.8.1.json" -> schema("2.8.1")))
        fixture.found("2.8.1") shouldBe Right("2.8.1" -> schema("2.8.1"))
        fixture.found("2.8.1") shouldBe Right("2.8.1" -> schema("2.8.1"))
        fixture.downloads.toList shouldBe List(baseUrl + "sdl-schema-2.8.1.json")
        fixture.repository.cachedSchema(version("2.8.1")) shouldBe defined
    }

    it should "fall back to the closest lower released version" in {
        val fixture = Fixture(Map("" -> index, "sdl-schema-2.8.0.json" -> schema("2.8.0"), "sdl-schema-2.8.1.json" -> schema("2.8.1")))
        fixture.found("2.8.5") shouldBe Right("2.8.1" -> schema("2.8.1"))
        fixture.found("2.9.2") shouldBe Right("2.8.1" -> schema("2.8.1")) // snapshots are not a fallback
        fixture.found("2.6.0").isLeft shouldBe true
    }

    it should "download snapshots again once outdated, keeping the cached one if offline" in {
        val fixture = Fixture(Map("sdl-schema-3.0.0-SNAPSHOT.json" -> schema("3.0.0-SNAPSHOT")), snapshotMaxAge = Duration.Zero)
        fixture.found("3.0.0-SNAPSHOT").isRight shouldBe true
        fixture.repository.cachedSchema(version("3.0.0-SNAPSHOT")) shouldBe None
        fixture.online = false
        fixture.found("3.0.0-SNAPSHOT") shouldBe Right("3.0.0-SNAPSHOT" -> schema("3.0.0-SNAPSHOT"))
        fixture.downloads.size should be >= 2
    }

    it should "only use the cache if downloading is disabled" in {
        val fixture = Fixture(Map("sdl-schema-2.8.0.json" -> schema("2.8.0")))
        fixture.found("2.8.0", download = false).isLeft shouldBe true
        fixture.downloads shouldBe empty
        fixture.found("2.8.0")
        fixture.found("2.8.3", download = false) shouldBe Right("2.8.0" -> schema("2.8.0")) // closest cached version
    }

    it should "reject invalid JSON" in {
        val fixture = Fixture(Map("sdl-schema-2.8.1.json" -> "<html>not found</html>"))
        fixture.found("2.8.1").left.value should include ("Invalid JSON")
        fixture.repository.cachedSchema(version("2.8.1")) shouldBe None
    }
