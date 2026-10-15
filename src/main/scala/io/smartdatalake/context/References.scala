package io.smartdatalake.context

/** Attributes referencing objects of another section by id */
object References:
  val DATA_OBJECTS = "dataObjects"
  val CONNECTIONS = "connections"
  val ACTIONS = "actions"

  private val sectionByAttribute = Map(
    "inputId" -> DATA_OBJECTS,
    "outputId" -> DATA_OBJECTS,
    "inputIds" -> DATA_OBJECTS,
    "outputIds" -> DATA_OBJECTS,
    "connectionId" -> CONNECTIONS
  )

  /** Section of the objects referenced by the given attribute, e.g. dataObjects for inputId */
  def referencedSection(attribute: String): Option[String] = sectionByAttribute.get(attribute)

  /** Removes quotes, brackets and separators around an id under the caret */
  def cleanId(word: String): String = word.trim.stripSuffix(",").replaceAll("""^[\["\s]+|[\]"\s,]+$""", "")
