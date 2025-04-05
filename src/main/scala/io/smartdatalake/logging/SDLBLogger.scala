package io.smartdatalake.logging

import org.slf4j.{Logger, LoggerFactory}
import ch.qos.logback.core.net.server.Client

trait SDLBLogger:
    self =>
    protected final lazy val logger: Logger = LoggerFactory.getLogger(self.getClass)

    private val patterns = Map(
        "apiKey" -> """(?<=key=)[A-Za-z0-9_-]+""",
        "password" -> """(?<=password=)[^&\s]+""",
        "token" -> """(?<=token=)[^&\s]+""",
        "secret" -> """(?<=secret=)[^&\s]+"""
    )

    private[logging] def anonymizeMessage(message: String, anonymize: Boolean = true): String =
        Option.when(anonymize) {
            patterns.foldLeft(message) { case (msg, (key, pattern)) =>
                msg.replaceAll(pattern, "[REDACTED]")
            }
        }.getOrElse(message)
            

    def trace(message: => String, anonymize: Boolean = true): Unit =
        logger.trace(anonymizeMessage(message, anonymize))

    def debug(message: => String, anonymize: Boolean = true): Unit =
        logger.debug(anonymizeMessage(message, anonymize))

    def info(message: => String, anonymize: Boolean = true): Unit =
        logger.info(anonymizeMessage(message, anonymize))
    
    def warn(message: => String, anonymize: Boolean = true): Unit =
        logger.warn(anonymizeMessage(message, anonymize))
    
    def error(message: => String, anonymize: Boolean = true): Unit =
        logger.error(anonymizeMessage(message, anonymize))
