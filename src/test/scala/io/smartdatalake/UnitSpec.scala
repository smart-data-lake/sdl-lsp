package io.smartdatalake

import org.scalatest.*
import org.scalatest.flatspec.*
import org.scalatest.matchers.*

import scala.io.Source
import scala.util.Using

abstract class UnitSpec extends AnyFlatSpec with should.Matchers with OptionValues with Inside with Inspectors:
  def loadFile(filePath: String): String =
    Using.resource(getClass.getClassLoader.getResourceAsStream(filePath)) { inputStream =>
      Source.fromInputStream(inputStream).getLines().mkString("\n").trim
    }
