package io.smartdatalake.context

import com.typesafe.config.{Config, ConfigException}
import io.smartdatalake.context.hocon.{HoconDocument, HoconParser}

/**
  * A version of the text of a file, parsed once and shared by everything seeing this version:
  * the workspace, the context of the file, navigation and diagnostics.
  * Typesafe Config parses it at once, as the validity of each version is needed to keep the last valid config;
  * the syntax tree is only parsed when needed.
  */
final class ParsedText(val uri: String, val text: String):
  val configOrError: Either[ConfigException, Config] = HoconParser.parseOrError(text, uri)

  /** Unresolved config of the text, None if the text is invalid */
  def config: Option[Config] = configOrError.toOption

  /** Syntax tree of the text, giving its structure and offsets */
  lazy val syntax: HoconDocument = HoconDocument.parse(text)

  override def toString: String = s"ParsedText($uri, ${text.take(50)})"
