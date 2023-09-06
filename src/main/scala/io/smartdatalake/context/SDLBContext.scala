package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigValue}
import io.smartdatalake.context.TextContext
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

case class SDLBContext private(textContext: TextContext, parentPath: String, parentWord: String, word: String, oIndex: Option[Int]) {

  def withText(newText: String): SDLBContext = copy(textContext = textContext.update(newText))

  def withCaretPosition(originalLine: Int, originalCol: Int): SDLBContext =
    val TextContext(originalText, configText, config) = textContext
    if originalLine <= 0 || originalLine > originalText.count(_ == '\n') + 1 || originalCol < 0 then this else
      val (newLine, newCol) = MultiLineTransformer.computeCorrectedPosition(originalText, originalLine, originalCol)
      val word = HoconParser.retrieveWordAtPosition(configText, newLine, newCol)
      val (parentLine, parentWord) = HoconParser.retrieveDirectParent(configText, newLine, newCol)
      val path = HoconParser.retrievePath(config, parentLine)
      val oIndex = HoconParser.findIndexIfInList(configText, newLine, newCol)
      copy(parentPath = path, parentWord = parentWord, word = word, oIndex = oIndex)


  //TODO keep that method?
  def getParentContext: Option[ConfigValue] = if parentPath.isBlank then None else Some(textContext.config.getValue(parentPath))


}

object SDLBContext {
  val EMPTY_CONTEXT: SDLBContext = SDLBContext(EMPTY_TEXT_CONTEXT, "", "", "", None)

  def fromText(originalText: String): SDLBContext = SDLBContext(TextContext.create(originalText), "", "", "", None)

}


