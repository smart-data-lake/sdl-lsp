package io.smartdatalake.context

trait ContextAdvisor:
  def generateSuggestions(context: SDLBContext): List[ContextSuggestion]
