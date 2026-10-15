package io.smartdatalake.context.hocon

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

    it should "mask comments and structural characters in strings, keeping positions" in {
        val text = "a { # comment {\n  b = \"x{y}#z\" // other\n  c = \"\"\"[1]\"\"\"\n}"
        val masked = HoconScanner.mask(text)
        masked.length shouldBe text.length
        masked shouldBe "a {            \n  b = \"x_y__z\"         \n  c = \"\"\"_1_\"\"\"\n}"
    }

    it should "keep escaped quotes inside strings" in {
        HoconScanner.mask("a = \"x\\\"{\" # c") shouldBe "a = \"x\\\"_\"    "
    }

