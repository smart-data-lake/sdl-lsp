package io.smartdatalake.navigation

import io.smartdatalake.UnitSpec

class HoconScannerSpec extends UnitSpec:

    "HOCON scanner" should "find the end of a block, ignoring braces in strings and comments" in {
        val text = "a {\n  b = \"}\" # }\n  c = \"\"\"{ } }\"\"\" // }\n  d { e = 1 }\n}\nf = 2"
        val end = HoconScanner.blockEnd(text, 0).value
        HoconScanner.lineOf(text, end) shouldBe 4
        text(end) shouldBe '}'
    }

    it should "not find a block if none is opened" in {
        HoconScanner.blockEnd("a = 1\n}", 0) shouldBe None
        HoconScanner.blockEnd("a {", 0) shouldBe None
    }

    it should "convert lines and offsets" in {
        val text = "ab\ncd\nef"
        HoconScanner.lineStart(text, 0) shouldBe 0
        HoconScanner.lineStart(text, 2) shouldBe 6
        HoconScanner.lineOf(text, 7) shouldBe 2
    }
