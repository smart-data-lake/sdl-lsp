package io.smartdatalake.schema

import scala.util.Try

/** Version of SDLB, e.g. 2.8.1 or 3.0.0-SNAPSHOT. A version with a qualifier comes before the release: 3.0.0-SNAPSHOT < 3.0.0 */
case class SdlbVersion private (value: String, numbers: List[Int], qualifier: Option[String]):
  def isSnapshot: Boolean = qualifier.exists(_.endsWith("SNAPSHOT"))
  override def toString: String = value

object SdlbVersion:
  // Only safe characters, as the version is part of file names and URLs
  private val PATTERN = """(\d+(?:\.\d+)*)(?:-([A-Za-z0-9._-]+))?""".r

  def parse(text: String): Option[SdlbVersion] = text.trim match
    case value @ PATTERN(numbers, qualifier) => Try(SdlbVersion(value, numbers.split('.').map(_.toInt).toList, Option(qualifier))).toOption
    case _ => None

  given Ordering[SdlbVersion] = (a, b) =>
    val length = math.max(a.numbers.size, b.numbers.size)
    val byNumbers = a.numbers.padTo(length, 0).zip(b.numbers.padTo(length, 0)).map(_ compare _).find(_ != 0).getOrElse(0)
    if byNumbers != 0 then byNumbers
    else (a.qualifier, b.qualifier) match
      case (None, None) => 0
      case (None, Some(_)) => 1
      case (Some(_), None) => -1
      case (Some(x), Some(y)) => x compare y
