package io.smartdatalake.context.hocon

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
 * Fault-tolerant parser of HOCON text into a concrete syntax tree, for the text in the middle of editing:
 * a field without value, unclosed objects and arrays or misplaced characters give syntax errors but never stop the parsing.
 * The values themselves are read by Typesafe Config: this parser only gives the structure and the offsets.
 */
object HoconSyntaxParser:
  def parse(text: String): HoconDocument =
    val (tokens, lexerErrors) = HoconLexer.tokenize(text)
    val parser = Parser(text, tokens.filter(_.kind != TokenKind.Comment))
    val root = parser.parseRoot()
    HoconDocument(text, tokens, root, (lexerErrors ++ parser.errors).sortBy(_.start))

  private val INCLUDE_TARGET = """(?:required\()?(?:file|url|classpath)\(.*""".r

  private class Parser(text: String, tokens: Vector[Token]):
    import TokenKind.*

    private var position = 0
    private val errorBuffer = ListBuffer[SyntaxError]()

    def errors: List[SyntaxError] = errorBuffer.toList

    private def current: Option[Token] = tokens.lift(position)
    private def currentKind: Option[TokenKind] = current.map(_.kind)
    private def currentStart: Int = current.map(_.start).getOrElse(text.length)
    private def advance(): Token =
      val token = tokens(position)
      position += 1
      token
    private def error(message: String, start: Int, end: Int): Unit = errorBuffer += SyntaxError(message, start, end)
    private def error(message: String, token: Token): Unit = error(message, token.start, token.end)

    private def skip(kinds: TokenKind*): Unit = while currentKind.exists(kinds.contains) do position += 1
    private def kindAfter(kinds: TokenKind*): Option[TokenKind] = tokens.drop(position).find(token => !kinds.contains(token.kind)).map(_.kind)

    private def isKeyToken(kind: TokenKind) = kind == Unquoted || kind == QuotedString
    private def isValueStart(kind: TokenKind) = kind match
      case Unquoted | QuotedString | MultilineString | Substitution | OpenBrace | OpenBracket => true
      case _ => false
    private def isSeparator(kind: TokenKind) = kind == Equals || kind == Colon || kind == PlusEquals

    def parseRoot(): ObjectNode =
      skip(Whitespace, Newline)
      if currentKind.contains(OpenBrace) then
        val root = parseObject(advance().start, braced = true, Set.empty)
        skip(Whitespace, Newline, Comma)
        current.foreach(token => error("Unexpected content after the root object", token.start, text.length))
        root
      else parseObject(0, braced = false, Set.empty)

    /** Fields until the closing brace, starting after the opening brace if any */
    private def parseObject(start: Int, braced: Boolean, enclosingClosers: Set[TokenKind]): ObjectNode =
      val fields = ListBuffer[FieldNode]()
      val includes = ListBuffer[IncludeNode]()
      def unclosed(end: Int) =
        if braced then error("Unclosed '{'", start, start + 1)
        ObjectNode(fields.toList, includes.toList, start, end, braced, closed = !braced)

      @tailrec
      def loop(): ObjectNode =
        skip(Whitespace, Newline, Comma)
        current match
          case None => unclosed(text.length)
          case Some(token) if token.kind == CloseBrace && braced =>
            advance()
            ObjectNode(fields.toList, includes.toList, start, token.end, braced, closed = true)
          case Some(token) if enclosingClosers.contains(token.kind) => unclosed(token.start)
          case Some(token) if isKeyToken(token.kind) =>
            parseInclude() match
              case Some(include) => includes += include
              case None => fields += parseField(if braced then enclosingClosers + CloseBrace else enclosingClosers)
            expectEndOfElement(if braced then enclosingClosers + CloseBrace else enclosingClosers, "field")
            loop()
          case Some(token) =>
            error(s"Unexpected '${token.text}', expected a key", token)
            advance()
            loop()
      loop()

    /** After a field or an array element, only a newline, a comma or a closing bracket may follow: skips anything else */
    private def expectEndOfElement(closers: Set[TokenKind], element: String): Unit =
      skip(Whitespace)
      current.filterNot(token => token.kind == Newline || token.kind == Comma || closers.contains(token.kind)).foreach { token =>
        val start = token.start
        while currentKind.exists(kind => kind != Newline && kind != Comma && !closers.contains(kind)) do advance()
        error(s"Expected a new line or ',' after the $element", start, tokens(position - 1).end)
      }

    /** `include "file"` or `include required(file("file"))` */
    private def parseInclude(): Option[IncludeNode] =
      val keyword = tokens(position)
      val target = tokens.drop(position + 1).dropWhile(_.kind == Whitespace).headOption
      target.filter(target => keyword.kind == Unquoted && keyword.text == "include" && target.start > keyword.end &&
        (target.kind == QuotedString || INCLUDE_TARGET.matches(text.substring(target.start, lineEnd(target.start))))
      ).map { target =>
        while currentKind.exists(kind => kind != Newline && kind != Comma && kind != CloseBrace) do advance()
        val end = tokens(position - 1).end
        IncludeNode(text.substring(target.start, end).trim, keyword.start, end)
      }

    private def lineEnd(from: Int): Int =
      val end = text.indexOf('\n', from)
      if end < 0 then text.length else end

    private def parseField(closers: Set[TokenKind]): FieldNode =
      val key = parseKey()
      val keyStart = key.headOption.map(_.start).getOrElse(currentStart)
      val keyEnd = tokens(position - 1).end
      skip(Whitespace)
      // The opening brace or the separator may be on the next line
      if currentKind.contains(Newline) && kindAfter(Whitespace, Newline).exists(kind => kind == OpenBrace || isSeparator(kind)) then skip(Whitespace, Newline)
      currentKind match
        case Some(kind) if isSeparator(kind) =>
          val separator = advance()
          skip(Whitespace)
          val value = parseValue(closers)
          if value.isEmpty then error("Missing value", separator)
          FieldNode(key, Some(separator), value, keyStart, value.map(_.end).getOrElse(separator.end), caretEnd(value))
        case Some(OpenBrace) =>
          val value = parseValue(closers)
          FieldNode(key, None, value, keyStart, value.map(_.end).getOrElse(keyEnd), caretEnd(value))
        case _ =>
          error("Expected '=', ':' or '{' after the key", keyStart, keyEnd)
          FieldNode(key, None, None, keyStart, keyEnd, keyEnd)

    /** The caret edits a field until the next token after its value, or until the closing bracket of its object or array value */
    private def caretEnd(value: Option[ValueNode]): Int = value.flatMap(_.parts.lastOption) match
      case Some(obj: ObjectNode) if obj.closed => obj.end - 1
      case Some(array: ArrayNode) if array.closed => array.end - 1
      case _ =>
        skip(Whitespace)
        currentStart

    /** Key tokens, possibly separated by spaces, split into segments on the dots outside of quotes */
    private def parseKey(): List[KeySegment] =
      val keyTokens = ListBuffer(advance())
      while currentKind.exists(isKeyToken) || currentKind.contains(Whitespace) && tokens.lift(position + 1).exists(token => isKeyToken(token.kind)) do
        keyTokens += advance()
      val first = keyTokens.head.contentStart
      // Segments in reverse order: the head is the segment being built
      keyTokens.foldLeft(List(KeySegment("", first, first))) { (segments, token) =>
        val current = segments.head
        token.kind match
          case Unquoted =>
            val parts = token.text.split("\\.", -1).toList
            val starts = parts.scanLeft(token.start)((offset, part) => offset + part.length + 1)
            val extended = if parts.head.isEmpty then current else current.copy(name = current.name + parts.head, end = token.start + parts.head.length)
            parts.tail.zip(starts.tail).map((part, start) => KeySegment(part, start, start + part.length)).reverse ++ (extended :: segments.tail)
          case QuotedString =>
            val start = if current.name.isEmpty then token.contentStart else current.start
            KeySegment(current.name + token.value, start, token.contentEnd) :: segments.tail
          case _ => current.copy(name = current.name + token.text, end = token.end) :: segments.tail
      }.reverse

    /** Parts of a value until the end of the line, a comma or a closing bracket */
    private def parseValue(closers: Set[TokenKind]): Option[ValueNode] =
      val parts = ListBuffer[SyntaxNode]()
      @tailrec
      def loop(): Unit = currentKind match
        case Some(Unquoted | QuotedString | MultilineString) =>
          parts += LiteralNode(advance())
          loop()
        case Some(Substitution) =>
          parts += SubstitutionNode(advance())
          loop()
        case Some(OpenBrace) =>
          parts += parseObject(advance().start, braced = true, closers)
          loop()
        case Some(OpenBracket) =>
          parts += parseArray(advance().start, closers)
          loop()
        case Some(Whitespace) if tokens.lift(position + 1).exists(token => isValueStart(token.kind)) =>
          parts += LiteralNode(advance()) // spaces between parts are part of the concatenation
          loop()
        case _ => ()
      loop()
      Option.when(parts.nonEmpty)(ValueNode(parts.toList, parts.head.start, parts.last.end))

    /** Elements until the closing bracket, starting after the opening bracket */
    private def parseArray(start: Int, enclosingClosers: Set[TokenKind]): ArrayNode =
      val elements = ListBuffer[ValueNode]()
      val closers = enclosingClosers + CloseBracket
      def unclosed(end: Int) =
        error("Unclosed '['", start, start + 1)
        ArrayNode(elements.toList, start, end, closed = false)

      @tailrec
      def loop(): ArrayNode =
        skip(Whitespace, Newline, Comma)
        current match
          case None => unclosed(text.length)
          case Some(token) if token.kind == CloseBracket =>
            advance()
            ArrayNode(elements.toList, start, token.end, closed = true)
          case Some(token) if enclosingClosers.contains(token.kind) => unclosed(token.start)
          case Some(token) if isValueStart(token.kind) =>
            elements ++= parseValue(closers)
            expectEndOfElement(closers, "array element")
            loop()
          case Some(token) =>
            error(s"Unexpected '${token.text}' in an array", token)
            advance()
            loop()
      loop()
