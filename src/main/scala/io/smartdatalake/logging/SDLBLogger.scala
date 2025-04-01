package io.smartdatalake.logging

import org.slf4j.{Logger, LoggerFactory}
import ch.qos.logback.core.net.server.Client

trait SDLBLogger:
    self =>
    protected final lazy val logger: Logger = LoggerFactory.getLogger(self.getClass)
    export logger.{debug, trace, info, warn, error}
