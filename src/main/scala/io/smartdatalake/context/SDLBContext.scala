package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.context.TextContext
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

import scala.annotation.tailrec

case class SDLBContext private(textContext: TextContext, parentPath: List[String], word: String, isInList: Boolean) {

  def withText(newText: String): SDLBContext = copy(textContext = textContext.update(newText))

  def withCaretPosition(originalLine: Int, originalCol: Int): SDLBContext =
    val TextContext(originalText, configText, config) = textContext
    if originalLine <= 0 || originalLine > originalText.count(_ == '\n') + 1 || originalCol < 0 then this else
      val (newLine, newCol) = MultiLineTransformer.computeCorrectedPosition(originalText, originalLine, originalCol)
      val word = HoconParser.retrieveWordAtPosition(configText, newLine, newCol)
      val (parentLine, _) = HoconParser.retrieveDirectParent(configText, newLine, newCol)
      val (parentPathInitialList, isParentListKind) = HoconParser.retrievePathList(config, parentLine)
      val oIndex = HoconParser.findIndexIfInList(configText, newLine, newCol)
      val parentPath = oIndex match
        case Some(index) => if isParentListKind then parentPathInitialList :+ index.toString else parentPathInitialList
        case None => parentPathInitialList
      val isInList = HoconParser.isInList(configText, newLine, newCol)
      copy(parentPath = parentPath, word = word, isInList = isInList)
      

  def getParentContext: Option[ConfigValue] =
    @tailrec
    def findParentContext(currentConfig: ConfigValue, remainingPath: List[String]): Option[ConfigValue] = remainingPath match
      case Nil => Some(currentConfig)
      case path::newRemainingPath => currentConfig match
        case asConfigObject: ConfigObject => findParentContext(asConfigObject.get(path), newRemainingPath)
        case asConfigList: ConfigList => findParentContext(asConfigList.get(path.toInt), newRemainingPath)
        case _ => None

    findParentContext(textContext.rootConfig.root(), parentPath)

}

object SDLBContext {
  val EMPTY_CONTEXT: SDLBContext = SDLBContext(EMPTY_TEXT_CONTEXT, List(), "", false)

  def fromText(originalText: String): SDLBContext = SDLBContext(TextContext.create(originalText), List(), "", false)

}


