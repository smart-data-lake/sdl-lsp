package io.smartdatalake.context

import com.typesafe.config.Config
import io.smartdatalake.context.TextContext.EMPTY_TEXT_CONTEXT
import io.smartdatalake.context.hocon.HoconParser
import io.smartdatalake.utils.MultiLineTransformer
import io.smartdatalake.logging.SDLBLogger
import scala.util.Try

case class TextContext private (uri: String, originalText: String, workspaceUriToContents: Map[String, String], configText: String, rootConfig: Config, isConfigCompleted: Boolean = true) extends SDLBLogger:

  def withContents(newContents: Map[String, String]): TextContext = copy(workspaceUriToContents=newContents)
  
  def update(newText: String): TextContext = this match
    case EMPTY_TEXT_CONTEXT => TextContext.create(uri, newText, workspaceUriToContents)
    case _ => updateContext(newText)

  private def updateContext(newText: String) =
    val newConfigText = MultiLineTransformer.flattenMultiLines(newText)
    val fullText = (newConfigText::workspaceUriToContents.removed(uri).values.toList).mkString("\n")
    val newConfigOption = HoconParser.parse(fullText)
    val isConfigCompleted = newConfigOption.isDefined
    val newConfig = newConfigOption.getOrElse(HoconParser.EMPTY_CONFIG)
    if newConfig == HoconParser.EMPTY_CONFIG then
      copy(originalText=newText, isConfigCompleted=isConfigCompleted)
    else
      val resolvedConfig = Try(newConfig.resolve()).getOrElse(newConfig)
      copy(originalText=newText, configText=newConfigText, rootConfig=resolvedConfig, isConfigCompleted=isConfigCompleted)

  override def toString: String = s"TextContext(originalText=${originalText.take(50)}, configText=${configText.take(50)}, rootConfig=${rootConfig.toString})"


object TextContext:
  val EMPTY_TEXT_CONTEXT: TextContext = new TextContext("", "", Map.empty, "", HoconParser.EMPTY_CONFIG)

  def create(uri: String, originalText: String, workspaceUriToContents: Map[String, String]): TextContext =
    val configText = MultiLineTransformer.flattenMultiLines(originalText)
    val fullText = (configText::workspaceUriToContents.removed(uri).values.toList).mkString("\n")
    val config = HoconParser.parse(fullText).getOrElse(HoconParser.EMPTY_CONFIG)
    val resolvedConfig = Try(config.resolve()).getOrElse(config)
    TextContext(uri, originalText, workspaceUriToContents, configText, resolvedConfig)
