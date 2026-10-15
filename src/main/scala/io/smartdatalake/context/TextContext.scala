package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigResolveOptions}
import io.smartdatalake.context.hocon.{HoconDocument, HoconParser}
import io.smartdatalake.logging.SDLBLogger
import io.smartdatalake.utils.Lazy
import scala.util.Try

/**
  * @param source current text of the file
  * @param othersConfig unresolved config of the other files of the workspace
  * @param resolvedConfig resolved config of the file merged with the other files, computed on first use:
  *                       the last valid one while the text is invalid
  */
case class TextContext private (source: ParsedText, othersConfig: Config, private val resolvedConfig: Lazy[Config]) extends SDLBLogger:

  def uri: String = source.uri
  def originalText: String = source.text
  def isConfigCompleted: Boolean = source.config.isDefined

  /** Resolved config of the file merged with the other files: the last valid one while the text is invalid */
  def rootConfig: Config = resolvedConfig.value

  /** Syntax tree of the current text, used to locate the caret */
  def syntax: HoconDocument = source.syntax

  def withOthersConfig(newOthersConfig: Config): TextContext = TextContext.build(source, newOthersConfig, resolvedConfig)

  def update(newText: String): TextContext = withSource(ParsedText(uri, newText))

  def withSource(newSource: ParsedText): TextContext = TextContext.build(newSource, othersConfig, resolvedConfig)

  override def toString: String = s"TextContext(originalText=${originalText.take(50)}, isConfigCompleted=$isConfigCompleted)"


object TextContext:
  val EMPTY_TEXT_CONTEXT: TextContext = create(ParsedText("", ""), HoconParser.EMPTY_CONFIG)

  def create(uri: String, originalText: String, othersConfig: Config): TextContext = create(ParsedText(uri, originalText), othersConfig)

  // An invalid file still sees the content of the other files
  def create(source: ParsedText, othersConfig: Config): TextContext =
    build(source, othersConfig, Lazy(mergeAndResolve(HoconParser.EMPTY_CONFIG, othersConfig)))

  /** While the text is invalid, the last valid config is kept */
  private def build(source: ParsedText, othersConfig: Config, lastValidConfig: Lazy[Config]): TextContext =
    val resolvedConfig = source.config match
      case Some(config) => Lazy(mergeAndResolve(config, othersConfig))
      case None => lastValidConfig
    new TextContext(source, othersConfig, resolvedConfig)

  /** The current file has priority over the other files. Substitutions are resolved as far as possible */
  private def mergeAndResolve(config: Config, othersConfig: Config): Config =
    val merged = config.withFallback(othersConfig)
    Try(merged.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))).getOrElse(merged)
