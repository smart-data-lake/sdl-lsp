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


  def retrieveWordAtPosition(text: String, line: Int, col: Int): String =
    def getWordAtIndex(textLine: String, index: Int): String =
      val (leftPart, rightPart) = textLine.splitAt(index)
      val leftPossibleIndex = leftPart.lastIndexOf(" ")
      val leftIndex = if leftPossibleIndex == -1 then 0 else leftPossibleIndex
      val rightPossibleIndex = rightPart.indexOf(" ")
      val rightIndex = if rightPossibleIndex == -1 then textLine.length - 2 else index + rightPossibleIndex
      textLine.substring(leftIndex, rightIndex).trim

    val textLine = text.split(Token.NEW_LINE_PATTERN, -1)(line-1) + " "
    val column = math.min(textLine.length-1, col)
    val leadingCharacter = textLine.charAt(column)
    column match
      case 0 if leadingCharacter.isWhitespace => ""
      case _ if leadingCharacter.isWhitespace =>
        val precedingCharacter = textLine.charAt(column-1)
        if precedingCharacter.isWhitespace then "" else getWordAtIndex(textLine, column-1)

      case _ => getWordAtIndex(textLine, column)


  type Position = (Int, Int)
  type ParentInfo = (Position, String)

  def retrieveParentPath(text: String, line: Int, col: Int): List[String] =
    @tailrec
    def retrievePathHelper(line: Int, col: Int, acc: List[String]): List[String] =
      val ((parentLine, parentCol), parentName) = retrieveDirectParent(text, line, col)
      val indexIfInList = findIndexIfInList(text, line, col)
      if parentName.isEmpty then acc
      else
        val newAcc = indexIfInList match
          case Some(idx) if idx >= 0 && isParentOutsideArray(text, line, col, parentLine, parentCol) => parentName :: idx.toString :: acc
          case _ => parentName :: acc
        retrievePathHelper(parentLine, parentCol, newAcc)

    retrievePathHelper(line-1, col, List.empty) // Line is 1-based

  def isParentOutsideArray(text: String, line: Int, col: Int, parentLine: Int, parentCol: Int): Boolean =
    val absoluteParentPosition = lineColToAbsolutePosition(text, parentLine, parentCol)
    val absolutePosition = lineColToAbsolutePosition(text, line, col)
    findListAreaFrom(text, absolutePosition).map(_._1  > absoluteParentPosition).getOrElse(false)

  /**
   * Retrieves the direct parent element at the specified position
   */
  def retrieveDirectParent(text: String, line: Int, column: Int): ParentInfo =
    val lines = text.split(Token.NEW_LINE_PATTERN, -1)
    val textLine = if line >= 0 && line < lines.length then
      lines(line).takeWhile(_ != Token.COMMENT).mkString
    else 
      ""
    val col = math.min(textLine.length, column)
    
    val keyValSplit = textLine.split(Token.KEY_VAL_SPLIT_REGEX)
    val keyName = keyValSplit.headOption
    val keyNameTrimmed = keyName.map(_.trim)
    if keyName.isDefined && col > keyName.get.length && !keyNameTrimmed.get.isEmpty then
      val colPos = textLine.indexOf(keyNameTrimmed.get)
      ((line, colPos), keyNameTrimmed.get)
    else
      retrieveParentRecursively(text, line, col)

  def findIndexIfInList(text: String, line: Int, column: Int): Option[Int] =
    val absolutePosition = lineColToAbsolutePosition(text, line, column)
    findListAreaFrom(text, absolutePosition) match
      case None => None
      case Some((startPosition, endPosition)) =>
        @tailrec
        def buildObjectPositions(currentPosition: Int, currentList: List[(Int, Int)]): List[(Int, Int)] =
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
  

  def findAreaFrom(text: String, position: Int, startToken: Char, endToken: Char): Option[(Int, Int)] =
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

  def lineColToAbsolutePosition(text: String, line: Int, column: Int): Int =
    val textLine = text.split(Token.NEW_LINE_PATTERN, -1)
    val nCharactersBeforeCurrentLine = textLine.take(line).map(line => line.length + 1).sum // +1 for \n character
    val nCharactersCurrentLine = math.min(textLine(line).length, column)
    nCharactersCurrentLine + nCharactersBeforeCurrentLine


  /**
   * Recursively retrieves a parent by traversing upward through the text
   */
  def retrieveParentRecursively(text: String, line: Int, col: Int): ParentInfo =
    val lines = text.split(Token.NEW_LINE_PATTERN, -1)
    
    @tailrec
    def retrieveHelper(line: Int, depth: Int): ParentInfo =
      if line < 0 then
        ((0, 0), "")
      else
        val origLine = lines(line)
        val textLine = origLine.filterNot(c => c.isWhitespace).takeWhile(_ != Token.COMMENT).mkString
        
        // Update depth
        val newDepth = depth + computeDepth(textLine)         
        
        if textLine.contains(Token.END_OBJECT) || textLine.contains(Token.END_LIST) then 
          retrieveHelper(line-1, newDepth) 
        else
          textLine.split(Token.KEY_VAL_SPLIT_REGEX) match
            case Array(singleBlock) => 
              if singleBlock.isBlank then 
                retrieveHelper(line-1, newDepth) 
              else if newDepth < 0 then 
                // Calculate the column for the parent block
                val colPos = origLine.indexOf(singleBlock.trim)
                
                ((line, colPos), singleBlock)
              else retrieveHelper(line-1, newDepth)
            case _ => retrieveHelper(line-1, newDepth)
    
    def computeDepth(textLine: String): Int =
      val startObjects = textLine.count(_ == Token.START_OBJECT)
      val endObjects = textLine.count(_ == Token.END_OBJECT)
      val startLists = textLine.count(_ == Token.START_LIST)
      val endLists = textLine.count(_ == Token.END_LIST)
      endObjects - startObjects + endLists - startLists
    
    val currentDepth = computeDepth(lines(line).take(col))
    retrieveHelper(line-1, currentDepth)