package io.smartdatalake.utils

import com.typesafe.config.{Config, ConfigRenderOptions, ConfigUtil}
import io.smartdatalake.UnitSpec
import io.smartdatalake.utils.MultiLineTransformer as MLT

import scala.io.Source
import scala.util.Using

class MultiLineTransformerSpec extends UnitSpec {

  private val text: String = loadFile("fixture/hocon/with-multi-lines-example.conf") //TODO add a fixture with mix-in flattened triple quotes and not


  "Multi line transformer" should "correctly flatten multi lines" in {
    val expectedFlattenedText = loadFile("fixture/hocon/with-multi-lines-flattened-example.conf")

    trimLines(MLT.flattenMultiLines(text)) should be (trimLines(expectedFlattenedText))
  }

  it should "compute correct mapping between original positions and new positions" in {
    MLT.computeCorrectedPositions(text) should be (List((1,0), (2,0), (3,0), (4,0), (5,0), (6,0), (7,0), (8,0), (9,0), (9,112), (9,130), (10,0), (11,0), (12,0), (13,0), (14,0), (15,0), (16,0), (17,0), (17,136), (17,300), (17,421), (18,0), (19,0), (20,0), (21,0), (22,0), (23,0), (24,0)))
  }

  it should "retrieve correct position for arbitrary original position" in {
    MLT.computeCorrectedPosition(text, 8, 0) should be ((8, 0))
    MLT.computeCorrectedPosition(text, 8, 15) should be ((8, 15))

    MLT.computeCorrectedPosition(text, 9, 0) should be ((9, 0))
    MLT.computeCorrectedPosition(text, 9, 15) should be ((9, 15))

    MLT.computeCorrectedPosition(text, 10, 0) should be ((9, 112))
    MLT.computeCorrectedPosition(text, 10, 15) should be ((9, 127))

    MLT.computeCorrectedPosition(text, 11, 0) should be ((9, 130))
    MLT.computeCorrectedPosition(text, 11, 15) should be ((9, 145))

    MLT.computeCorrectedPosition(text, 12, 0) should be((10, 0))
    MLT.computeCorrectedPosition(text, 12, 15) should be((10, 15))

    MLT.computeCorrectedPosition(text, 22, 0) should be((17, 421))
    MLT.computeCorrectedPosition(text, 22, 15) should be((17, 436))

  }



  def trimLines(s: String): String =
    s.split("\n").map(_.trim).mkString

}
