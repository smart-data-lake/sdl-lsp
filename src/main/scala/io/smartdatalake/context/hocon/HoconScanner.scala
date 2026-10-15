package io.smartdatalake.context.hocon

import scala.annotation.tailrec

/** Lexical helpers on HOCON text, aware of strings and comments */
object HoconScanner:

  /** Offset of the brace closing the first block opened at or after the given offset, None if there is none */
  def blockEnd(text: String, from: Int): Option[Int] =
    @tailrec
    def scan(i: Int, depth: Int): Option[Int] =
      if i >= text.length then None
      else if text.startsWith("\"\"\"", i) then
        val end = text.indexOf("\"\"\"", i + 3)
        if end < 0 then None else scan(end + 3, depth)
      else text(i) match
        case '"' => scan(stringEnd(text, i + 1), depth)
        case '#' => scan(lineEnd(text, i), depth)
        case '/' if text.startsWith("//", i) => scan(lineEnd(text, i), depth)
        case '{' => scan(i + 1, depth + 1)
        case '}' if depth == 1 => Some(i)
        case '}' if depth > 1 => scan(i + 1, depth - 1)
        case '}' => None // closing an enclosing block: no block was opened
        case _ => scan(i + 1, depth)
    scan(from, 0)

  /**
   * Same text, with the same positions, where comments are blanked and the characters with a structural meaning
   * inside strings are replaced, so the structure can be analysed on the raw text
   */
  def mask(text: String): String =
    val masked = new StringBuilder(text)
    @tailrec
    def scan(i: Int): Unit =
      if i < text.length then
        if text.startsWith("\"\"\"", i) then
          val end = text.indexOf("\"\"\"", i + 3)
          val stringEnd = if end < 0 then text.length else end
          maskStructure(masked, i + 3, stringEnd)
          scan(stringEnd + 3)
        else text(i) match
          case '"' =>
            val end = stringEnd(text, i + 1)
            maskStructure(masked, i + 1, end - 1)
            scan(end)
          case '#' => scan(blank(masked, i, lineEnd(text, i)))
          case '/' if text.startsWith("//", i) => scan(blank(masked, i, lineEnd(text, i)))
          case _ => scan(i + 1)
    scan(0)
    masked.toString

  private val STRUCTURAL_CHARACTERS = Set('{', '}', '[', ']', '#', '=', ':', '/', ',')

  private def maskStructure(masked: StringBuilder, from: Int, until: Int): Unit =
    (from until math.min(until, masked.length)).foreach(i => if STRUCTURAL_CHARACTERS.contains(masked(i)) then masked(i) = '_')

  /** Blanks the characters until the given offset (excluded) and returns it */
  private def blank(masked: StringBuilder, from: Int, until: Int): Int =
    (from until until).foreach(i => if masked(i) != '\r' then masked(i) = ' ')
    until

  /** Line (0-based) of the given offset */
  def lineOf(text: String, offset: Int): Int = text.substring(0, math.min(offset, text.length)).count(_ == '\n')

  /** Offset of the start of the given line (0-based) */
  def lineStart(text: String, line: Int): Int =
    @tailrec
    def loop(offset: Int, remaining: Int): Int =
      if remaining == 0 || offset < 0 then math.max(offset, 0)
      else
        val next = text.indexOf('\n', offset)
        if next < 0 then text.length else loop(next + 1, remaining - 1)
    loop(0, line)

  private def lineEnd(text: String, from: Int): Int =
    val end = text.indexOf('\n', from)
    if end < 0 then text.length else end

  /** Offset after the closing quote of a string starting at the given offset (after the opening quote) */
  @tailrec
  private def stringEnd(text: String, from: Int): Int =
    if from >= text.length then text.length
    else text(from) match
      case '\\' => stringEnd(text, from + 2)
      case '"' => from + 1
      case '\n' => from // unterminated string
      case _ => stringEnd(text, from + 1)
