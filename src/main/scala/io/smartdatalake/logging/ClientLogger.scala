package io.smartdatalake.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import ch.qos.logback.classic.PatternLayout
import org.eclipse.lsp4j.{MessageParams, MessageType, LogTraceParams}
import org.eclipse.lsp4j.services.LanguageClient

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ClientLogger:
    var lspClient: Option[LanguageClient] = None

class ClientLogger extends AppenderBase[ILoggingEvent]:
    // Create a layout for formatting
    private var pattern: String = "%date{dd-MM-yyyy HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

    def setPattern(pattern: String): Unit =
        this.pattern = pattern

    private lazy val layout = {
        val patternLayout = new PatternLayout()
        patternLayout.setContext(getContext)
        patternLayout.setPattern(pattern)
        patternLayout.start()
        patternLayout
    }
    
    override def start(): Unit =
        if (getContext != null) then
            super.start()
    
    override def append(event: ILoggingEvent): Unit =
        if event.getLoggerName != "jsonRpcLogger" then
            ClientLogger.lspClient.foreach { client =>
                try
                    // Get the properly formatted message with timestamp, level, etc.
                    val formattedMessage = layout.doLayout(event)
                    
                    // Map log level to LSP message type
                    val messageType = event.getLevel.levelInt match
                        case x if x >= 40000 => MessageType.Error   // ERROR, FATAL
                        case x if x >= 30000 => MessageType.Warning // WARN
                        case x if x >= 20000 => MessageType.Info    // INFO
                        case _ => MessageType.Log                   // DEBUG, TRACE
                    
                    // Send to the client
                    client.logMessage(new MessageParams(messageType, formattedMessage.trim))
                catch
                    case ex: Exception =>
                        System.err.println(s"Error sending log to LSP client: ${ex.getMessage}")
            }