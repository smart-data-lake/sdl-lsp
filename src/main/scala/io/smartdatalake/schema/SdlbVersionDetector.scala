package io.smartdatalake.schema

import org.w3c.dom.{Element, Node}

import java.nio.file.{Files, Path}
import javax.xml.parsers.DocumentBuilderFactory
import scala.annotation.tailrec
import scala.util.Try

/**
  * Finds the SDLB version of a Maven project in its pom.xml: the version of the parent io.smartdatalake:sdl-parent,
  * else of a dependency io.smartdatalake:sdl-*, with ${...} placeholders resolved from the properties of the pom.
  * The modules of the root pom are searched too. Versions inherited from a parent pom that is not sdl-parent are not found.
  */
object SdlbVersionDetector:
  private val GROUP_ID = "io.smartdatalake"
  private val PARENT_ARTIFACT_ID = "sdl-parent"
  private val PLACEHOLDER = """\$\{([^}]+)\}""".r

  /** The SDLB version and the pom.xml it was found in */
  def detect(projectRoot: Path): Option[(String, Path)] =
    val rootPom = projectRoot.resolve("pom.xml")
    readPom(rootPom).flatMap { root =>
      sdlbVersion(root, Map.empty).map(_ -> rootPom).orElse {
        // A module may inherit the properties of the root pom
        val inherited = properties(root)
        children(root, "modules").flatMap(children(_, "module")).iterator
          .map(module => modulePom(rootPom.getParent.resolve(module.getTextContent.trim)))
          .flatMap(pom => readPom(pom).flatMap(sdlbVersion(_, inherited)).map(_ -> pom))
          .nextOption()
      }
    }

  private def modulePom(path: Path): Path = if Files.isDirectory(path) then path.resolve("pom.xml") else path

  private def readPom(pom: Path): Option[Element] = Option.when(Files.isRegularFile(pom))(pom).flatMap { _ =>
    Try {
      val factory = DocumentBuilderFactory.newInstance()
      // A pom never needs a DTD: refusing them also prevents XML external entities
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setExpandEntityReferences(false)
      factory.newDocumentBuilder().parse(pom.toFile).getDocumentElement
    }.toOption
  }

  private def sdlbVersion(project: Element, inheritedProperties: Map[String, String]): Option[String] =
    val parent = children(project, "parent").headOption
    val parentVersion = parent.flatMap(text(_, "version"))
    val projectVersion = text(project, "version").orElse(parentVersion)
    val variables = inheritedProperties ++ properties(project) ++
      parentVersion.map(v => Map("project.parent.version" -> v, "parent.version" -> v)).getOrElse(Map.empty) ++
      projectVersion.map(v => Map("project.version" -> v, "version" -> v)).getOrElse(Map.empty)

    val fromParent = parent.filter(isSdlb(_, _ == PARENT_ARTIFACT_ID)).flatMap(text(_, "version"))
    val dependencies = (children(project, "dependencies") ++ children(project, "dependencyManagement").flatMap(children(_, "dependencies")))
      .flatMap(children(_, "dependency"))
    val fromDependencies = dependencies.filter(isSdlb(_, _.startsWith("sdl-"))).flatMap(text(_, "version"))
    (fromParent.toList ++ fromDependencies).iterator.flatMap(resolve(_, variables)).nextOption()

  private def isSdlb(element: Element, artifactId: String => Boolean): Boolean =
    text(element, "groupId").contains(GROUP_ID) && text(element, "artifactId").exists(artifactId)

  private def properties(project: Element): Map[String, String] =
    children(project, "properties").flatMap(childElements).map(property => property.getTagName -> property.getTextContent.trim).toMap

  /** The value with its placeholders replaced, None if one of them is unknown */
  private def resolve(value: String, variables: Map[String, String]): Option[String] =
    @tailrec
    def loop(current: String, remainingDepth: Int): Option[String] =
      if !current.contains("${") then Some(current)
      else if remainingDepth == 0 then None
      else
        val replaced = PLACEHOLDER.replaceAllIn(current, m => java.util.regex.Matcher.quoteReplacement(variables.getOrElse(m.group(1), m.matched)))
        if replaced == current then None else loop(replaced, remainingDepth - 1)
    loop(value.trim, 10)

  private def childElements(element: Element): List[Element] =
    val nodes = element.getChildNodes
    (0 until nodes.getLength).map(nodes.item).collect { case child: Element if child.getNodeType == Node.ELEMENT_NODE => child }.toList

  private def children(element: Element, name: String): List[Element] = childElements(element).filter(_.getTagName == name)

  private def text(element: Element, name: String): Option[String] = children(element, name).headOption.map(_.getTextContent.trim).filter(_.nonEmpty)
