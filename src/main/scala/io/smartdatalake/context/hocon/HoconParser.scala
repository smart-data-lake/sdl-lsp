package io.smartdatalake.context.hocon

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigParseOptions}

/** Parsing of HOCON text into a config by Typesafe Config. The structure and offsets of the text are given by HoconSyntaxParser */
private[context] object HoconParser:

  /**
   * Parse the given text
   * @param text in hocon format
   * @return parsed text in config format
   */
  def parse(text: String, originDescription: String = ""): Option[Config] =
    parseOrError(text, originDescription).toOption

  /** The parsed config, or the error of the first invalid element */
  def parseOrError(text: String, originDescription: String = ""): Either[ConfigException, Config] =
    val options = ConfigParseOptions.defaults().setOriginDescription(Option(originDescription).filter(_.nonEmpty).orNull)
    try Right(ConfigFactory.parseString(text, options))
    catch case e: ConfigException => Left(e)

  val EMPTY_CONFIG: Config = ConfigFactory.parseString("")
