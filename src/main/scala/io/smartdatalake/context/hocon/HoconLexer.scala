package io.smartdatalake.context.hocon

import scala.annotation.tailrec

enum TokenKind:
  case OpenBrace, CloseBrace, OpenBracket, CloseBracket, Equals, Colon, PlusEquals, Comma
  case Newline, Whitespace, Comment
  case QuotedString, MultilineString, Unquoted, Substitution

/** A token of HOCON text, from start (included) to end (excluded) */
case class Token(kind: TokenKind, start: Int, end: Int, text: String):
  import TokenKind.*

  def isString: Boolean = kind == QuotedString || kind == MultilineString || kind == Unquoted

  /** Offsets of the content of the token, without quotes */
  def contentStart: Int = kind match
    case QuotedString if text.length >= 2 => start + 1
    case MultilineString if text.length >= 6 => start + 3
    case _ => start

  def contentEnd: Int = kind match
    case QuotedString if text.length >= 2 && text.last == '"' => end - 1
    case MultilineString if text.length >= 6 && text.endsWith("\"\"\"") => end - 3
    case _ => end

  /** Value of a string token: unquoted and unescaped */
  def value: String = kind match
    case QuotedString => HoconLexer.unescape(text.substring(contentStart - start, contentEnd - start))
    case _ => text.substring(contentStart - start, contentEnd - start)

/** A syntax error of HOCON text, from start (included) to end (excluded) */
case class SyntaxError(message: String, start: Int, end: Int)

/** Splits HOCON text into tokens. Every character belongs to exactly one token, so the text can be rebuilt from them */
object HoconLexer:
  import TokenKind.*

  private val SINGLE_CHARACTER_TOKENS = Map('{' -> OpenBrace, '}' -> CloseBrace, '[' -> OpenBracket, ']' -> CloseBracket,
    '=' -> Equals, ':' -> Colon, ',' -> Comma, '\n' -> Newline)
  private val UNQUOTED_STOP = Set('{', '}', '[', ']', '=', ':', ',', '"', '#', '\n')

  def tokenize(text: String): (Vector[Token], List[SyntaxError]) =
    val tokens = Vector.newBuilder[Token]
    val errors = List.newBuilder[SyntaxError]

    @tailrec
    def loop(start: Int): Unit = if start < text.length then
      def token(kind: TokenKind, end: Int): Int =
        tokens += Token(kind, start, end, text.substring(start, end))
        end
      val c = text(start)
      val end =
        if SINGLE_CHARACTER_TOKENS.contains(c) then token(SINGLE_CHARACTER_TOKENS(c), start + 1)
        else if isWhitespace(c) then token(Whitespace, skipWhitespace(text, start))
        else if c == '#' || text.startsWith("//", start) then token(Comment, lineEnd(text, start))
        else if text.startsWith("\"\"\"", start) then
          val close = text.indexOf("\"\"\"", start + 3)
          if close < 0 then
            errors += SyntaxError("Unterminated multi-line string", start, text.length)
            token(MultilineString, text.length)
          else token(MultilineString, extraQuotesEnd(text, close + 3)) // quotes before the closing ones belong to the string
        else if c == '"' then
          val (end, terminated) = quotedEnd(text, start + 1)
          if !terminated then errors += SyntaxError("Unterminated string", start, end)
          token(QuotedString, end)
        else if text.startsWith("${", start) then
          val close = text.indexWhere(ch => ch == '}' || ch == '\n', start + 2)
          if close < 0 || text(close) == '\n' then
            val end = if close < 0 then text.length else close
            errors += SyntaxError("Unterminated substitution", start, end)
            token(Substitution, end)
          else token(Substitution, close + 1)
        else if text.startsWith("+=", start) then token(PlusEquals, start + 2)
        else token(Unquoted, unquotedEnd(text, start + 1))
      loop(end)

    loop(0)
    (tokens.result(), errors.result())

  def isWhitespace(c: Char): Boolean = c != '\n' && (Character.isWhitespace(c) || c == ' ' || c == '﻿')

  @tailrec
  private def skipWhitespace(text: String, from: Int): Int =
    if from < text.length && isWhitespace(text(from)) then skipWhitespace(text, from + 1) else from

  private def lineEnd(text: String, from: Int): Int =
    val end = text.indexOf('\n', from)
    if end < 0 then text.length else end

  @tailrec
  private def extraQuotesEnd(text: String, from: Int): Int =
    if from < text.length && text(from) == '"' then extraQuotesEnd(text, from + 1) else from

  /** Offset after the closing quote of a string starting at the given offset (after the opening quote), and whether it is closed */
  @tailrec
  private def quotedEnd(text: String, from: Int): (Int, Boolean) =
    if from >= text.length then (text.length, false)
    else text(from) match
      case '\\' if from + 1 < text.length && text(from + 1) != '\n' => quotedEnd(text, from + 2)
      case '"' => (from + 1, true)
      case '\n' => (from, false)
      case _ => quotedEnd(text, from + 1)

  @tailrec
  private def unquotedEnd(text: String, from: Int): Int =
    if from >= text.length then from
    else
      val c = text(from)
      if UNQUOTED_STOP.contains(c) || isWhitespace(c) || text.startsWith("//", from) || text.startsWith("+=", from) || text.startsWith("${", from) then from
      else unquotedEnd(text, from + 1)

  private[hocon] def unescape(content: String): String =
    if !content.contains('\\') then content
    else
      val result = new StringBuilder
      @tailrec
      def loop(i: Int): Unit = if i < content.length then
        if content(i) == '\\' && i + 1 < content.length then
          content(i + 1) match
            case 'n' => result += '\n'; loop(i + 2)
            case 't' => result += '\t'; loop(i + 2)
            case 'r' => result += '\r'; loop(i + 2)
            case 'b' => result += '\b'; loop(i + 2)
            case 'f' => result += '\f'; loop(i + 2)
            case 'u' if i + 6 <= content.length && content.substring(i + 2, i + 6).forall(Character.digit(_, 16) >= 0) =>
              result += Integer.parseInt(content.substring(i + 2, i + 6), 16).toChar; loop(i + 6)
            case other => result += other; loop(i + 2)
        else
          result += content(i)
          loop(i + 1)
      loop(0)
      result.toString
