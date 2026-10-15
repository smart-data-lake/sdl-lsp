package io.smartdatalake.context.hocon

/** Node of the concrete syntax tree of a HOCON text, from start (included) to end (excluded) */
sealed trait SyntaxNode:
  def start: Int
  def end: Int

/**
 * Object, with or without braces (the root object usually has none).
 * An unclosed object ends where the parser gave up on it: at the end of the text or before the bracket closing an enclosing array.
 */
case class ObjectNode(fields: List[FieldNode], includes: List[IncludeNode], start: Int, end: Int, braced: Boolean, closed: Boolean) extends SyntaxNode

/**
 * Field of an object. A dotted key is split into its segments: `a.b = 1` has the key a, b.
 * @param caretEnd last offset where the caret still edits this field: after the value and the spaces following it on the line,
 *                 but before the bracket closing an object or array value
 */
case class FieldNode(key: List[KeySegment], separator: Option[Token], value: Option[ValueNode], start: Int, end: Int, caretEnd: Int) extends SyntaxNode:
  def keyNames: List[String] = key.map(_.name)

/** Segment of a key, from start to end of its content (without quotes) */
case class KeySegment(name: String, start: Int, end: Int)

/** Value made of one or several concatenated parts, e.g. `${base} { a = 1 }` */
case class ValueNode(parts: List[SyntaxNode], start: Int, end: Int) extends SyntaxNode:
  /** The value as a string, if it is made of strings only */
  def stringValue: Option[String] =
    Option.when(parts.forall(_.isInstanceOf[LiteralNode]))(parts.collect { case literal: LiteralNode => literal.token.value }.mkString)
  /** Offsets of the content of the value, without quotes for a single string */
  def contentStart: Int = parts match
    case List(LiteralNode(token)) => token.contentStart
    case _ => start
  def contentEnd: Int = parts match
    case List(LiteralNode(token)) => token.contentEnd
    case _ => end
  def objects: List[ObjectNode] = parts.collect { case obj: ObjectNode => obj }
  def array: Option[ArrayNode] = parts match
    case List(array: ArrayNode) => Some(array)
    case _ => None

case class ArrayNode(elements: List[ValueNode], start: Int, end: Int, closed: Boolean) extends SyntaxNode

/** String, number, boolean or null; spaces between other parts of a value are literals too */
case class LiteralNode(token: Token) extends SyntaxNode:
  def start: Int = token.start
  def end: Int = token.end

case class SubstitutionNode(token: Token) extends SyntaxNode:
  def start: Int = token.start
  def end: Int = token.end
  def optional: Boolean = token.text.startsWith("${?")
  def path: String = token.text.stripPrefix("${").stripPrefix("?").stripSuffix("}").trim

case class IncludeNode(target: String, start: Int, end: Int) extends SyntaxNode

/** Occurrence of a path in the text: the segment of the given field for the last element of the path */
case class PathMatch(field: FieldNode, segmentIndex: Int):
  def segment: KeySegment = field.key(segmentIndex)
  /** Whether the path ends at the end of the key, so that the value of the field is the value of the path */
  def isWholeKey: Boolean = segmentIndex == field.key.size - 1
  def value: Option[ValueNode] = if isWholeKey then field.value else None

/** Parsed HOCON text: the syntax tree, the tokens and the syntax errors */
case class HoconDocument(text: String, tokens: Vector[Token], root: ObjectNode, errors: List[SyntaxError]):
  import TokenKind.*

  private lazy val lineStarts: Vector[Int] = (0 +: text.indices.filter(text(_) == '\n').map(_ + 1)).toVector

  /** Offset of the given 0-based line and column, the column being limited to the line */
  def offsetAt(line: Int, column: Int): Int =
    if line < 0 then 0
    else if line >= lineStarts.size then text.length
    else
      val start = lineStarts(line)
      val lineEnd = if line + 1 < lineStarts.size then lineStarts(line + 1) - 1 else text.length
      val contentEnd = if lineEnd > start && text(lineEnd - 1) == '\r' then lineEnd - 1 else lineEnd
      start + math.max(0, math.min(column, contentEnd - start))

  /** 0-based line and column of the given offset */
  def positionAt(offset: Int): (Int, Int) =
    val bounded = math.max(0, math.min(offset, text.length))
    val line = lineStarts.search(bounded) match
      case scala.collection.Searching.Found(index) => index
      case scala.collection.Searching.InsertionPoint(index) => index - 1
    (line, bounded - lineStarts(line))

  /** All fields of the text, in objects and in arrays */
  lazy val allFields: List[FieldNode] =
    def inValue(value: ValueNode): List[FieldNode] = value.parts.flatMap {
      case obj: ObjectNode => inObject(obj)
      case array: ArrayNode => array.elements.flatMap(inValue)
      case _ => Nil
    }
    def inObject(obj: ObjectNode): List[FieldNode] = obj.fields.flatMap(field => field :: field.value.toList.flatMap(inValue))
    inObject(root)

  /**
   * Path of the element edited at the given offset, e.g. `actions, a1, inputId` after `inputId =`.
   * In a key, it is the path of the object containing the key. Objects in arrays are given by their index.
   */
  def parentPathAt(offset: Int): List[String] = pathInObject(root, offset)

  private def pathInObject(obj: ObjectNode, offset: Int): List[String] =
    obj.fields.find(field => field.start < offset && offset <= field.caretEnd) match
      case None => Nil
      case Some(field) =>
        val inValue = field.separator match
          case Some(separator) => offset >= separator.end
          case None => field.value.exists(offset > _.start)
        if inValue then field.keyNames ++ field.value.map(pathInValue(_, offset)).getOrElse(Nil)
        else field.key.init.takeWhile(_.end < offset).map(_.name) // the segments before the one being edited

  private def pathInValue(value: ValueNode, offset: Int): List[String] =
    value.parts.collectFirst {
      case obj: ObjectNode if contains(obj, offset) => pathInObject(obj, offset)
      case array: ArrayNode if contains(array, offset) =>
        array.elements.zipWithIndex.collectFirst {
          case (element, index) if element.parts.exists(contains(_, offset)) => index.toString :: pathInValue(element, offset)
        }.getOrElse(Nil)
    }.getOrElse(Nil)

  /** Whether the offset is inside the brackets of the given object or array */
  private def contains(node: SyntaxNode, offset: Int): Boolean = node match
    case obj: ObjectNode => offset > obj.start && (if obj.closed then offset < obj.end else offset <= obj.end)
    case array: ArrayNode => offset > array.start && (if array.closed then offset < array.end else offset <= array.end)
    case _ => false

  /**
   * Word ending at or containing the given offset, else starting at it: a segment of a key, the value of a string, or a structural character.
   * Empty in comments and between words.
   */
  def wordAt(offset: Int): String =
    val touching = tokens.filter(token => token.start <= offset && offset <= token.end && token.kind != Whitespace && token.kind != Newline)
    if touching.exists(token => token.kind == Comment && token.start < offset) then ""
    else
      val (words, others) = touching.filter(_.kind != Comment).partition(token => token.isString || token.kind == Substitution)
      words.find(_.start < offset).orElse(words.headOption) match
        case Some(token) if token.kind == TokenKind.Unquoted && isInKey(offset) =>
          // Only the segment of a dotted key
          val index = offset - token.start
          val from = token.text.lastIndexOf('.', index - 1) + 1
          val to = token.text.indexOf('.', index) match
            case -1 => token.text.length
            case dot => dot
          token.text.substring(from, math.max(from, to))
        case Some(token) => if token.isString then token.value else token.text
        case None => others.find(_.start < offset).orElse(others.headOption).map(_.text).getOrElse("")

  private def isInKey(offset: Int): Boolean = allFields.exists(_.key.exists(segment => segment.start <= offset && offset <= segment.end))

  /** Path of the key segment at the given offset, from the root, e.g. `templates, t1` on t1 in `templates { t1 {...} }`. None outside of keys */
  def keyPathAt(offset: Int): Option[List[String]] =
    allFields.find(_.key.exists(segment => segment.start <= offset && offset <= segment.end)).map { field =>
      val index = field.key.indexWhere(segment => segment.start <= offset && offset <= segment.end)
      pathTo(field) ++ field.key.take(index + 1).map(_.name)
    }

  /** Path of the object containing the given field. Objects in arrays are given by their index */
  private def pathTo(target: FieldNode): List[String] =
    def inObject(obj: ObjectNode, prefix: List[String]): Option[List[String]] = obj.fields.iterator.map { field =>
      if field eq target then Some(prefix)
      else field.value.flatMap(inValue(_, prefix ++ field.keyNames))
    }.collectFirst { case Some(path) => path }
    def inValue(value: ValueNode, prefix: List[String]): Option[List[String]] = value.parts.iterator.map {
      case obj: ObjectNode => inObject(obj, prefix)
      case array: ArrayNode => array.elements.iterator.zipWithIndex.map((element, index) => inValue(element, prefix :+ index.toString)).collectFirst { case Some(path) => path }
      case _ => None
    }.collectFirst { case Some(path) => path }
    inObject(root, Nil).getOrElse(Nil)

  /** Substitutions of the text, e.g. `${templates.t1}`, with the segments of their path */
  lazy val substitutions: List[(Token, List[KeySegment])] =
    tokens.filter(_.kind == Substitution).map(token => token -> HoconDocument.substitutionSegments(token)).toList

  /**
   * Path of the substitution at the given offset: up to the segment at the offset, the whole path elsewhere in the substitution,
   * e.g. `templates` on templates in `${templates.t1}`. The segment of the last element gives its location.
   */
  def substitutionPathAt(offset: Int): Option[List[KeySegment]] =
    substitutions.collectFirst { case (token, segments) if token.start <= offset && offset <= token.end && segments.nonEmpty =>
      segments.indexWhere(segment => segment.start <= offset && offset <= segment.end) match
        case -1 => segments
        case index => segments.take(index + 1)
    }

  /** Occurrences of the given path in the text, e.g. `dataObjects, obj` in `dataObjects { obj {...} }` or `dataObjects.obj = {...}` */
  def find(path: List[String]): List[PathMatch] =
    def inObject(obj: ObjectNode, prefix: List[String]): List[PathMatch] = obj.fields.flatMap { field =>
      val names = prefix ++ field.keyNames
      if path.size > prefix.size && path.size <= names.size then
        Option.when(names.take(path.size) == path)(PathMatch(field, path.size - prefix.size - 1)).toList
      else if path.size > names.size && path.startsWith(names) then
        field.value.toList.flatMap(_.objects).flatMap(inObject(_, names))
      else Nil
    }
    inObject(root, Nil)

  /** Occurrences of the elements directly under the given occurrence of a path, in the order of the text */
  def children(parent: PathMatch): List[PathMatch] =
    if !parent.isWholeKey then List(PathMatch(parent.field, parent.segmentIndex + 1))
    else parent.field.value.toList.flatMap(_.objects).flatMap(_.fields).map(PathMatch(_, 0))

object HoconDocument:
  def parse(text: String): HoconDocument = HoconSyntaxParser.parse(text)

  /** Segments of the path of a substitution token, split at the dots outside of quotes, without quotes and surrounding spaces */
  def substitutionSegments(token: Token): List[KeySegment] =
    val text = token.text
    val contentStart = if text.startsWith("${?") then 3 else 2
    val contentEnd = if text.endsWith("}") then text.length - 1 else text.length
    val dots = (contentStart until contentEnd).filter(i => text(i) == '.' && text.substring(contentStart, i).count(_ == '"') % 2 == 0)
    (contentStart +: dots.map(_ + 1)).zip(dots :+ contentEnd).toList.flatMap { (from, to) =>
      val piece = text.substring(from, to)
      val trimmedStart = from + piece.indexWhere(!_.isWhitespace)
      val trimmedEnd = from + piece.lastIndexWhere(!_.isWhitespace) + 1
      Option.when(trimmedStart >= from && trimmedEnd > trimmedStart)(
        KeySegment(text.substring(trimmedStart, trimmedEnd).replace("\"", ""), token.start + trimmedStart, token.start + trimmedEnd))
    }
