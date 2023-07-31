package io.smartdatalake.utils

import scala.annotation.tailrec

object MultiLineTransformer {

  def flattenMultiLines(text: String): String =
    val pattern = raw"""(?s)\"\"\".*?\"\"\"""".r
    pattern.replaceAllIn(text, m => m.matched.replace("\n", ""))


  def computeCorrectedPosition(text: String, lineNumber: Int, columnNumber: Int): (Int, Int) =
    val (line, columnShift) = computeCorrectedPositions(text)(lineNumber-1)
    (line, columnNumber +  columnShift)


  def computeCorrectedPositions(text: String): List[(Int, Int)] =
    case class State(isInMultiLine: Boolean, lineNumber: Int, columnShift: Int)
    text.split("\n")
      .foldLeft(List(State(false, 1, 0))) {(states, line) =>
        val lastState = states.head
        val isInTripleQuotes = lastState.isInMultiLine ^ line.contains("\"\"\"")
        if isInTripleQuotes then
          State(isInTripleQuotes, lastState.lineNumber, lastState.columnShift + line.length)::states
        else
          State(isInTripleQuotes, lastState.lineNumber+1, 0)::states
      }.map(state => (state.lineNumber, state.columnShift)).reverse
  
}
