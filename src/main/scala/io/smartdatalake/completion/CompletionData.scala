package io.smartdatalake.completion

import java.util.Base64
import java.nio.charset.StandardCharsets
import io.circe.syntax.*
import io.circe.generic.auto.*
import io.smartdatalake.logging.SDLBLogger

case class CompletionData(withTabStops: Boolean, parentPath: String, context: String):
    def toJson: String = 
        val encodedContext = Base64.getEncoder.encodeToString(
            context.getBytes(StandardCharsets.UTF_8)
        )
        copy(context = encodedContext).asJson.noSpaces

object CompletionData extends SDLBLogger:
    def fromJson(data: String): Option[CompletionData] =
        val jsonStr = Option.when(data.startsWith("\"") && data.endsWith("\"")){
            io.circe.parser.parse(data).toOption
                .flatMap(_.asString)
                .getOrElse(data)
        }.getOrElse(data)
        
        io.circe.parser.decode[CompletionData](jsonStr).toOption.map { completionData =>
            completionData.copy(context = new String(Base64.getDecoder.decode(completionData.context), StandardCharsets.UTF_8))
        }

