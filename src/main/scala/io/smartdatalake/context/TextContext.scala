package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigResolveOptions}
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.{HoconDocument, HoconParser}
import io.smartdatalake.logging.SDLBLogger
import io.smartdatalake.utils.Lazy
import scala.util.Try

/**
  * @param originalText current text of the file
  * @param othersConfig unresolved config of the other files of the workspace
  * @param rootConfig resolved config of the file merged with the other files: the last valid one while the text is invalid
  * @param parsedSyntax syntax tree of the current text, parsed once per text
  */
case class TextContext private (uri: String, originalText: String, othersConfig: Config, rootConfig: Config, isConfigCompleted: Boolean,
                                private val parsedSyntax: Lazy[HoconDocument]) extends SDLBLogger:

  /** Syntax tree of the current text, used to locate the caret */
  def syntax: HoconDocument = parsedSyntax.value

  def withOthersConfig(newOthersConfig: Config): TextContext = copy(othersConfig = newOthersConfig)

  def update(newText: String): TextContext = this match
    case EMPTY_TEXT_CONTEXT => TextContext.create(uri, newText, othersConfig)
    case _ => updateContext(newText)

  private def updateContext(newText: String) =
    // The same text is given again to merge it with a new config of the other files
    val newSyntax = if newText == originalText then parsedSyntax else TextContext.lazySyntax(newText)
    // While the text is invalid, keep the last valid config but always locate the caret in the current text
    HoconParser.parse(newText, uri) match
      case None => copy(originalText=newText, isConfigCompleted=false, parsedSyntax=newSyntax)
      case Some(config) => copy(originalText=newText, rootConfig=TextContext.mergeAndResolve(config, othersConfig), isConfigCompleted=true, parsedSyntax=newSyntax)

  override def toString: String = s"TextContext(originalText=${originalText.take(50)}, rootConfig=${rootConfig.toString.take(200)})"


object TextContext:
  val EMPTY_TEXT_CONTEXT: TextContext = new TextContext("", "", HoconParser.EMPTY_CONFIG, HoconParser.EMPTY_CONFIG, true, lazySyntax(""))

  def create(uri: String, originalText: String, othersConfig: Config): TextContext =
    val config = HoconParser.parse(originalText, uri)
    // An invalid file still sees the content of the other files
    val rootConfig = mergeAndResolve(config.getOrElse(HoconParser.EMPTY_CONFIG), othersConfig)
    TextContext(uri, originalText, othersConfig, rootConfig, config.isDefined, lazySyntax(originalText))

  private def lazySyntax(text: String): Lazy[HoconDocument] = Lazy(HoconDocument.parse(text))

  /** The current file has priority over the other files. Substitutions are resolved as far as possible */
  private def mergeAndResolve(config: Config, othersConfig: Config): Config =
    val merged = config.withFallback(othersConfig)
    Try(merged.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))).getOrElse(merged)
