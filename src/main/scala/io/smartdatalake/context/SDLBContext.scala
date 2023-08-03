package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import io.smartdatalake.context.SDLBContext.EMPTY_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

class SDLBContext private (val text: String, val config: Config, val parentPath: String, val parentWord: String) { //TODO hashing text or not hashing text?
  import SDLBContext.createContext

  def update(originalText: String, originalLine: Int, originalCol: Int): SDLBContext = this match
    case EMPTY_CONTEXT => createContext(originalText, originalLine, originalCol)
    case _ => updateContext(originalText, originalLine, originalCol)

  private def updateContext(originalText: String, originalLine: Int, originalCol: Int): SDLBContext =
    createContext(originalText, originalLine, originalCol) // For now. We'll see how to optimize incr. parsing later

  /**
   * get context of the parent
   * @return either a SimpleConfigObject if parent is a key or a ConfigString, ConfigList, ConfigBoolean etc if it is an end value
   */
  def getParentContext: Option[ConfigValue] = if parentPath.isBlank then None else Some(config.getValue(parentPath))

}


object SDLBContext {
  val EMPTY_CONTEXT = new SDLBContext("", HoconParser.EMPTY_CONFIG, "", "")

  def createContext(originalText: String, originalLine: Int, originalCol: Int): SDLBContext =
    if originalLine <= 0 || originalLine > originalText.count(_ == '\n') + 1 || originalCol < 0 then EMPTY_CONTEXT else
      val newText = MultiLineTransformer.flattenMultiLines(originalText)
      val (newLine, newCol) = MultiLineTransformer.computeCorrectedPosition(originalText, originalLine, originalCol)
      val config = HoconParser.parse(newText).getOrElse(HoconParser.EMPTY_CONFIG)
      val (parentLine, word) = HoconParser.retrieveDirectParent(newText, newLine, newCol)
      val path = HoconParser.retrievePath(config, parentLine)
      new SDLBContext(newText, config, path, word)
}
