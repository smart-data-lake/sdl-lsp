package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigException, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer

import scala.annotation.tailrec
import io.smartdatalake.logging.SDLBLogger

case class SDLBContext private(textContext: TextContext, parentPath: List[String], word: String) extends SDLBLogger {
  export textContext.{isConfigCompleted, rootConfig}

  def withText(newText: String): SDLBContext = copy(textContext = textContext.update(newText))

  def withOthersConfig(othersConfig: Config): SDLBContext =
    copy(textContext = textContext.withOthersConfig(othersConfig))

  def withCaretPosition(originalLine: Int, originalCol: Int): SDLBContext =
    val TextContext(_, originalText, _, configText, _, _) = textContext
    if originalLine <= 0 || originalLine > originalText.count(_ == '\n') + 1 || originalCol < 0 then this else
      val (newLine, newCol) = MultiLineTransformer.computeCorrectedPosition(originalText, originalLine, originalCol)
      val word = HoconParser.retrieveWordAtPosition(configText, newLine, newCol)
      val parentPath = HoconParser.retrieveParentPath(configText, newLine, newCol)
      copy(parentPath = parentPath, word = word)
      

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

  /** Unresolved config of the given text, None if the text is invalid */
  def parse(uri: String, text: String): Option[Config] = HoconParser.parse(text, uri)

  /** Unresolved config of the given text, or the parsing error */
  def parseOrError(uri: String, text: String): Either[ConfigException, Config] = HoconParser.parseOrError(text, uri)

  val EMPTY_CONFIG: Config = HoconParser.EMPTY_CONFIG

}


