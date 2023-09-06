package io.smartdatalake.context.hocon

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigList, ConfigObject, ConfigValue}
import io.smartdatalake.utils.MultiLineTransformer.{computeCorrectedPosition, flattenMultiLines}

import java.util.Map.Entry as JEntry

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import io.smartdatalake.context.hocon.HoconTokens as Token
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*

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
    def matchTypeValueAndSearchRecursive(key: String, configValue: ConfigValue, currentPath: String): Option[String] = {
      configValue match
        case configList: ConfigList => configList.toScala.zipWithIndex.flatMap{ (config, index) => matchTypeValueAndSearchRecursive(index.toString, config, currentPath + "." + key)}.headOption
        case configObject: ConfigObject => searchPath(configObject, currentPath + "." + key)
        case _ => None
    }

    def searchPath(currentConfig: ConfigObject, currentPath: String): Option[String] =
      import scala.jdk.CollectionConverters._

      val entrySet = currentConfig.entrySet().asScala

      entrySet.find(_.getValue.origin().lineNumber() == line) match
        case Some(entry) =>
          Some(currentPath + "." + entry.getKey)
        case None =>
          entrySet.flatMap { entry => matchTypeValueAndSearchRecursive(entry.getKey, entry.getValue, currentPath)
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
        val newDepth = depth - textLine.count(_ == Token.START_OBJECT) + textLine.count(_ == Token.END_OBJECT) - textLine.count(_ == Token.END_LIST) + textLine.count(_ == Token.START_LIST)
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


  def findIndexIfInList(text: String, line: Int, column: Int): Option[Int] =
    val absolutePosition = lineColToAbsolutePosition(text, line, column)
    findListAreaFrom(text, absolutePosition) match
      case None => None
      case Some((startPosition, endPosition)) =>
        @tailrec
        def buildObjectPositions(currentPosition: Int, currentList: List[(Int, Int)]): List[(Int, Int)] = //TODO unit test?
          val nextStartObjectTokenRelativePosition = text.substring(currentPosition).indexOf(Token.START_OBJECT)
          if nextStartObjectTokenRelativePosition == -1 then
            currentList
          else
            val nextStartObjectTokenAbsolutePosition = nextStartObjectTokenRelativePosition + currentPosition
            if nextStartObjectTokenAbsolutePosition > endPosition then
              currentList
            else
              findObjectAreaFrom(text, nextStartObjectTokenAbsolutePosition + 1) match // +1 to enter in the object
                case None => currentList
                case Some((start, end)) => buildObjectPositions(end + 1, (start, end)::currentList)

        val objectPositions = buildObjectPositions(startPosition, List.empty[(Int, Int)]).reverse
        objectPositions
          .zipWithIndex
          .find{(bounds, _) => absolutePosition >= bounds(0) && absolutePosition <= bounds(1)}
          .map(_._2)





  private[hocon] def findObjectAreaFrom(text: String, position: Int): Option[(Int, Int)] = findAreaFrom(text, position, Token.START_OBJECT, Token.END_OBJECT)
  private[hocon] def findListAreaFrom(text: String, position: Int): Option[(Int, Int)] = findAreaFrom(text, position, Token.START_LIST, Token.END_LIST)

  private def findAreaFrom(text: String, position: Int, startToken: Char, endToken: Char): Option[(Int, Int)] =
    @tailrec
    def indexOfWithDepth(char: Char, oppositeChar: Char, position: Int, direction: 1 | -1, depth: Int=0): Int =
      assert(depth >= 0)
      if position < 0 || position == text.length then -1 else text(position) match
      case c if c == char && depth == 0 => position
      case c if c == char               => indexOfWithDepth(char, oppositeChar, position + direction, direction, depth - 1)
      case c if c == oppositeChar       => indexOfWithDepth(char, oppositeChar, position + direction, direction, depth + 1)
      case _                            => indexOfWithDepth(char, oppositeChar, position + direction, direction, depth)

    val startPosition = indexOfWithDepth(startToken, endToken, position-1, -1)
    val endPosition = indexOfWithDepth(endToken, startToken, position, 1)
    if startPosition != -1 && endPosition != -1 then Some((startPosition+1, endPosition)) else None // (...+1, ...-1) to exclude list characters themselves

  private[hocon] def lineColToAbsolutePosition(text: String, line: Int, column: Int): Int =
    val textLine = text.split(Token.NEW_LINE)
    val nCharactersBeforeCurrentLine = textLine.take(line-1).map(line => line.length + 1).sum // +1 for \n character
    val nCharactersCurrentLine = math.min(textLine(line-1).length, column)
    nCharactersCurrentLine + nCharactersBeforeCurrentLine