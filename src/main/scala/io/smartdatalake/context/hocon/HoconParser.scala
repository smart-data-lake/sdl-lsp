package io.smartdatalake.context.hocon

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigObject}
import io.smartdatalake.utils.MultiLineTransformer.{computeCorrectedPosition, flattenMultiLines}

import scala.annotation.tailrec
import scala.util.{Try, Success, Failure}
import io.smartdatalake.context.hocon.{HoconTokens => Token}

/**
 * Utility class to parse HOCON-formatted files
 * Note that this utility class has a restricted scope to context,
 * this is because there need to be a consistence between the text given in argument of the methods
 * and the text given in the config file, which is not guaranteed if the user of the class is not aware of that.
 */
private[context] object HoconParser:

  /**
   * Parse the given text
   * @param text in hocon format
   * @return parsed text in config format
   */
  def parse(text: String): Option[Config] =
    Try(ConfigFactory.parseString(text)).toOption

  val EMPTY_CONFIG: Config = ConfigFactory.parseString("")


  /**
   * Find the path corresponding to the line number
   * @param config a config representing the HOCON file
   * @param line line number
   * @return path in format "a.b.c"
   */
  def retrievePath(config: Config, line: Int): String =
    def searchPath(currentConfig: ConfigObject, currentPath: String): Option[String] =
      import scala.jdk.CollectionConverters._

      val entrySet = currentConfig.entrySet().asScala

      entrySet.find(_.getValue.origin().lineNumber() == line) match
        case Some(entry) =>
          Some(currentPath + "." + entry.getKey)
        case None =>
          entrySet.flatMap { entry => // small caviat: still continue to look at the neighbor's level of the parent of the endpoint.
            entry.getValue match
              case configObject: ConfigObject => searchPath(configObject, currentPath + "." + entry.getKey)
              case _ => None
          }.headOption

    searchPath(config.root(), "").getOrElse("").stripPrefix(".")


  /**
   * Retrieve the direct parent of the current caret position.
   * If the caret position is in a value, retrieve its key directly.
   * If the caret position is in a key, retrieve its parent key.
   * @param text given text
   * @param line line number of caret position. 1-based number
   * @param column column number of caret position. 0-based number
   * @return The direct parent
   */
  def retrieveDirectParent(text: String, line: Int, column: Int): (Int, String) =

    @tailrec
    /*
    Note that retrieveHelper has not the exact same logic than retrieveDirectParent.
    */
    def retrieveHelper(line: Int, depth: Int): (Int, String) = {
      if line <= 0 then
        (0, "")
      else
        val textLine = text.split(Token.NEW_LINE)(line-1).filterNot(c => c.isWhitespace || c == Token.START_LIST || c == Token.END_LIST).takeWhile(_ != Token.COMMENT).mkString
        val newDepth = depth - textLine.count(_ == Token.START_OBJECT) + textLine.count(_ == Token.END_OBJECT) - textLine.count(_ == Token.END_LIST) + textLine.count(_ == Token.START_LIST) //TODO handle list better
        if textLine.contains(Token.END_OBJECT) then retrieveHelper(line-1, newDepth) else
          textLine.split(Token.KEY_VAL_SPLIT_REGEX) match
            case Array(singleBlock) => if singleBlock.isBlank then retrieveHelper(line-1, newDepth) else if depth == 0 then (line, singleBlock) else retrieveHelper(line-1, newDepth)
            case _ => retrieveHelper(line-1, newDepth)
    }

    val textLine = text.split(Token.NEW_LINE)(line-1).takeWhile(_ != Token.COMMENT).mkString
    val col = math.min(textLine.length, column)
    if textLine.count(_ == Token.END_OBJECT) > textLine.count(_ == Token.START_OBJECT) then retrieveHelper(line-1, if col > textLine.indexOf(Token.END_OBJECT) then 1 else 0) else
      val keyValSplit = textLine.split(Token.KEY_VAL_SPLIT_REGEX)
      if col > keyValSplit(0).length then
        val keyName = keyValSplit(0).trim
        (if keyName.isBlank then 0 else line, keyName)
      else
        retrieveHelper(line-1, 0)


  def retrieveWordAtPosition(text: String, line: Int, col: Int): String =
    def getWordAtIndex(textLine: String, index: Int): String =
      val (leftPart, rightPart) = textLine.splitAt(index)
      val leftPossibleIndex = leftPart.lastIndexOf(" ")
      val leftIndex = if leftPossibleIndex == -1 then 0 else leftPossibleIndex
      val rightPossibleIndex = rightPart.indexOf(" ")
      val rightIndex = if rightPossibleIndex == -1 then textLine.length - 2 else index + rightPossibleIndex
      textLine.substring(leftIndex, rightIndex).trim

    val textLine = text.split(Token.NEW_LINE)(line-1) + " "
    val column = math.min(textLine.length-1, col)
    val leadingCharacter = textLine.charAt(column)
    column match
      case 0 if leadingCharacter.isWhitespace => ""
      case _ if leadingCharacter.isWhitespace =>
        val precedingCharacter = textLine.charAt(column-1)
        if precedingCharacter.isWhitespace then "" else getWordAtIndex(textLine, column-1)

      case _ => getWordAtIndex(textLine, column)

