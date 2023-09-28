package io.smartdatalake.context

import com.typesafe.config.Config
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

case class TextContext private (originalText: String, configText: String, rootConfig: Config) {

  def update(newText: String): TextContext = this match
    case EMPTY_TEXT_CONTEXT => TextContext.create(newText)
    case _ => updateContext(newText)

  private def updateContext(newText: String) =
    val newConfigText = MultiLineTransformer.flattenMultiLines(newText)
    val newConfig = HoconParser.parse(newConfigText).getOrElse(HoconParser.EMPTY_CONFIG)
    if newConfig == HoconParser.EMPTY_CONFIG then this else TextContext(newText, newConfigText, newConfig)

  override def toString: String = s"TextContext(originalText=${originalText.take(50)}, configText=${configText.take(50)}, rootConfig=${rootConfig.toString.take(50)})"


}

object TextContext {
  val EMPTY_TEXT_CONTEXT: TextContext = new TextContext("", "", HoconParser.EMPTY_CONFIG)

  def create(originalText: String): TextContext =
    val configText = MultiLineTransformer.flattenMultiLines(originalText)
    val config = HoconParser.parse(configText).getOrElse(HoconParser.EMPTY_CONFIG)
    TextContext(originalText, configText, config)
}
