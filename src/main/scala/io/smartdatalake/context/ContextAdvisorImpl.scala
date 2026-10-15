package io.smartdatalake.context

import com.typesafe.config.{ConfigObject, ConfigValueType}

import scala.util.Try
import io.smartdatalake.conversions.ScalaJavaConverterAPI.*


class ContextAdvisorImpl extends ContextAdvisor:
  override def generateSuggestions(context: SDLBContext): List[ContextSuggestion] =
    context.parentPath.lastOption.flatMap(References.referencedSection) match
      case Some(section) => retrieveIds(context, section)
      case None => List.empty[ContextSuggestion]

  /** Ids of the objects of the section, with their type as label */
  private def retrieveIds(context: SDLBContext, section: String): List[ContextSuggestion] =
    Option(context.textContext.rootConfig.root().get(section)) match
      case Some(sectionObject: ConfigObject) => sectionObject.toScala.map { (id, value) =>
        val objectType = value match
          case obj: ConfigObject => Option(obj.get("type")).filter(_.valueType == ConfigValueType.STRING).flatMap(v => Try(v.unwrapped.toString).toOption)
          case _ => None
        ContextSuggestion(id, objectType.getOrElse(""))
      }.toList
      case _ => List.empty[ContextSuggestion]
