package io.smartdatalake.schema

import io.smartdatalake.UnitSpec

import java.nio.file.{Files, Path}

class SdlbVersionDetectorSpec extends UnitSpec:

    private def project(poms: (String, String)*): Path =
        val root = Files.createTempDirectory("sdl-lsp-project")
        poms.foreach { (path, content) =>
            val file = root.resolve(path)
            Files.createDirectories(file.getParent)
            Files.writeString(file, s"""<?xml version="1.0" encoding="UTF-8"?>
                |<project xmlns="http://maven.apache.org/POM/4.0.0">$content</project>""".stripMargin)
        }
        root

    private def detect(root: Path): Option[(String, String)] =
        SdlbVersionDetector.detect(root).map((version, pom) => version -> root.relativize(pom).toString.replace('\\', '/'))

    "SDLB version detector" should "read the version of the sdl-parent parent" in {
        val root = project("pom.xml" -> "<parent><groupId>io.smartdatalake</groupId><artifactId>sdl-parent</artifactId><version>3.0.0-SNAPSHOT</version></parent>")
        detect(root) shouldBe Some("3.0.0-SNAPSHOT" -> "pom.xml")
    }

    it should "read the version of an SDLB dependency, resolving properties" in {
        val root = project("pom.xml" ->
            """<parent><groupId>com.mycompany</groupId><artifactId>parent</artifactId><version>5</version></parent>
              |<properties><sdlb.version>2.8.1</sdlb.version></properties>
              |<dependencies>
              |  <dependency><groupId>org.apache.spark</groupId><artifactId>spark-core_2.12</artifactId><version>3.5.0</version></dependency>
              |  <dependency><groupId>io.smartdatalake</groupId><artifactId>sdl-core_${scala.minor.version}</artifactId><version>${sdlb.version}</version></dependency>
              |</dependencies>""".stripMargin)
        detect(root) shouldBe Some("2.8.1" -> "pom.xml")
    }

    it should "resolve the version of the parent" in {
        val root = project("pom.xml" ->
            """<parent><groupId>io.smartdatalake</groupId><artifactId>sdl-parent</artifactId><version>${revision}</version></parent>
              |<properties><revision>2.9.0</revision></properties>
              |<dependencies><dependency><groupId>io.smartdatalake</groupId><artifactId>sdl-core_2.13</artifactId><version>${project.parent.version}</version></dependency></dependencies>""".stripMargin)
        detect(root) shouldBe Some("2.9.0" -> "pom.xml")
    }

    it should "search the modules of the root pom, with the properties of the root pom" in {
        val root = project(
            "pom.xml" -> "<properties><sdlb.version>2.7.2</sdlb.version></properties><modules><module>common</module><module>pipelines</module></modules>",
            "common/pom.xml" -> "<artifactId>common</artifactId>",
            "pipelines/pom.xml" -> "<dependencyManagement><dependencies><dependency><groupId>io.smartdatalake</groupId><artifactId>sdl-core_2.12</artifactId><version>${sdlb.version}</version></dependency></dependencies></dependencyManagement>"
        )
        detect(root) shouldBe Some("2.7.2" -> "pipelines/pom.xml")
    }

    it should "find nothing without SDLB, with unknown properties or without pom" in {
        detect(project("pom.xml" -> "<dependencies><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0</version></dependency></dependencies>")) shouldBe None
        detect(project("pom.xml" -> "<dependencies><dependency><groupId>io.smartdatalake</groupId><artifactId>sdl-core_2.12</artifactId><version>${unknown}</version></dependency></dependencies>")) shouldBe None
        detect(Files.createTempDirectory("sdl-lsp-empty")) shouldBe None
    }

    it should "refuse poms with a DTD" in {
        val root = Files.createTempDirectory("sdl-lsp-dtd")
        Files.writeString(root.resolve("pom.xml"), """<?xml version="1.0"?><!DOCTYPE project [<!ENTITY v "2.8.1">]>
            |<project><parent><groupId>io.smartdatalake</groupId><artifactId>sdl-parent</artifactId><version>&v;</version></parent></project>""".stripMargin)
        detect(root) shouldBe None
    }

    "SDLB version" should "be ordered, a snapshot before its release" in {
        val versions = List("3.0.0", "2.10.0", "3.0.0-SNAPSHOT", "2.9.1-SNAPSHOT", "2.9.0").flatMap(SdlbVersion.parse)
        versions.sorted.map(_.value) shouldBe List("2.9.0", "2.9.1-SNAPSHOT", "2.10.0", "3.0.0-SNAPSHOT", "3.0.0")
        summon[Ordering[SdlbVersion]].equiv(SdlbVersion.parse("2.9").value, SdlbVersion.parse("2.9.0").value) shouldBe true
        SdlbVersion.parse("3.0.0-SNAPSHOT").value.isSnapshot shouldBe true
        SdlbVersion.parse("../../etc/passwd") shouldBe None
        SdlbVersion.parse("2.8.1/x") shouldBe None
    }
