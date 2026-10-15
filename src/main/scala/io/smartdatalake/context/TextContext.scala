package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigResolveOptions}
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer
import io.smartdatalake.logging.SDLBLogger
import scala.util.Try

/**
  * @param originalText current text of the file
  * @param othersConfig unresolved config of the other files of the workspace
  * @param configText current text with flattened multi-line strings, used to locate the caret
  * @param rootConfig resolved config of the file merged with the other files: the last valid one while the text is invalid
  */
case class TextContext private (uri: String, originalText: String, othersConfig: Config, configText: String, rootConfig: Config, isConfigCompleted: Boolean = true) extends SDLBLogger:

  def withOthersConfig(newOthersConfig: Config): TextContext = copy(othersConfig = newOthersConfig)

  def update(newText: String): TextContext = this match
    case EMPTY_TEXT_CONTEXT => TextContext.create(uri, newText, othersConfig)
    case _ => updateContext(newText)

  private def updateContext(newText: String) =
    val newConfigText = MultiLineTransformer.flattenMultiLines(newText)
    // While the text is invalid, keep the last valid config but always locate the caret in the current text
    HoconParser.parse(newText, uri) match
      case None => copy(originalText=newText, configText=newConfigText, isConfigCompleted=false)
      case Some(config) => copy(originalText=newText, configText=newConfigText, rootConfig=TextContext.mergeAndResolve(config, othersConfig), isConfigCompleted=true)

  override def toString: String = s"TextContext(originalText=${originalText.take(50)}, configText=${configText.take(50)}, rootConfig=${rootConfig.toString.take(200)})"


object TextContext:
  val EMPTY_TEXT_CONTEXT: TextContext = new TextContext("", "", HoconParser.EMPTY_CONFIG, "", HoconParser.EMPTY_CONFIG)

  def create(uri: String, originalText: String, othersConfig: Config): TextContext =
    val configText = MultiLineTransformer.flattenMultiLines(originalText)
    val config = HoconParser.parse(originalText, uri)
    // An invalid file still sees the content of the other files
    val rootConfig = mergeAndResolve(config.getOrElse(HoconParser.EMPTY_CONFIG), othersConfig)
    TextContext(uri, originalText, othersConfig, configText, rootConfig, config.isDefined)

  /** The current file has priority over the other files. Substitutions are resolved as far as possible */
  private def mergeAndResolve(config: Config, othersConfig: Config): Config =
    val merged = config.withFallback(othersConfig)
    Try(merged.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))).getOrElse(merged)
