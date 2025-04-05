package io.smartdatalake.logging

import io.smartdatalake.UnitSpec

class SDLBLoggerSpec extends UnitSpec {
    private val message = """
    |Request failed: Exception when sending request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=WuwdVii2NmdawPPo_rG3rf0
    |incorrect password=A!-.?1234
    |incorrect token=1234aaVVfe
    |fetching key does not work: secret=X_!?1234
        """.stripMargin

    private val logger = new SDLBLogger{}

    
    "SDLB Logger" should "anonymize messages per default" in {

        val expectedAnonymizedMessage = """
        |Request failed: Exception when sending request: POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=[REDACTED]
        |incorrect password=[REDACTED]
        |incorrect token=[REDACTED]
        |fetching key does not work: secret=[REDACTED]
        """.stripMargin
        logger.anonymizeMessage(message) shouldBe expectedAnonymizedMessage
    }

    it should "not anonymized messages if asked explicitely" in {
        logger.anonymizeMessage(message, anonymize = false) shouldBe message
    }
}
