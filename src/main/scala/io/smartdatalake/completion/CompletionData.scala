package io.smartdatalake.completion

import io.circe.syntax.*
import io.circe.generic.auto.*

/** Small data attached to completion items, sent to the client and back when resolving an item */
case class CompletionData(withTabStops: Boolean, parentPath: String):
    def toJson: String = this.asJson.noSpaces

object CompletionData:
    def fromJson(data: String): Option[CompletionData] =
        // LSP4J may give back the data as a JSON string containing the JSON object
        val jsonStr = Option.when(data.startsWith("\"") && data.endsWith("\"")){
            io.circe.parser.parse(data).toOption
                .flatMap(_.asString)
                .getOrElse(data)
        }.getOrElse(data)
        io.circe.parser.decode[CompletionData](jsonStr).toOption
