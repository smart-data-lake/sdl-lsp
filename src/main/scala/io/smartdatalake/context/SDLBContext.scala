package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser

import scala.annotation.tailrec
import io.smartdatalake.logging.SDLBLogger

case class SDLBContext private(textContext: TextContext, parentPath: List[String], word: String) extends SDLBLogger {
  export textContext.{isConfigCompleted, rootConfig}

  def withText(newText: String): SDLBContext = copy(textContext = textContext.update(newText))

  def withSource(source: ParsedText): SDLBContext = copy(textContext = textContext.withSource(source))

  def withOthersConfig(othersConfig: Config): SDLBContext =
    copy(textContext = textContext.withOthersConfig(othersConfig))

  /** @param originalLine 1-based line of the caret */
  def withCaretPosition(originalLine: Int, originalCol: Int): SDLBContext =
    val text = textContext.originalText
    if originalLine <= 0 || originalLine > text.count(_ == '\n') + 1 || originalCol < 0 then this else
      val syntax = textContext.syntax
      val offset = syntax.offsetAt(originalLine - 1, originalCol)
      copy(parentPath = syntax.parentPathAt(offset), word = syntax.wordAt(offset))

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
  val EMPTY_CONTEXT: SDLBContext = SDLBContext(EMPTY_TEXT_CONTEXT, List(), "")

  /** @param othersConfig unresolved config of the other files of the workspace */
  def fromText(uri: String, originalText: String, othersConfig: Config = HoconParser.EMPTY_CONFIG): SDLBContext =
    SDLBContext(TextContext.create(uri, originalText, othersConfig), List(), "")

  def fromText(originalText: String): SDLBContext = fromText("", originalText)

  /** @param othersConfig unresolved config of the other files of the workspace */
  def fromSource(source: ParsedText, othersConfig: Config): SDLBContext =
    SDLBContext(TextContext.create(source, othersConfig), List(), "")

  val EMPTY_CONFIG: Config = HoconParser.EMPTY_CONFIG

}


