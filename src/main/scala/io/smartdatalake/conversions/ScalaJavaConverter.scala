package io.smartdatalake.conversions

import com.typesafe.config.{ConfigList, ConfigValue}

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.messages

import scala.concurrent.Future
import scala.jdk.FutureConverters.*
import scala.jdk.CollectionConverters.*
import java.util.List as JList
import java.util.Set as JSet
import java.util.Map as JMap

trait ScalaJavaConverter {

  extension [T] (f: Future[T]) def toJava: CompletableFuture[T] = f.asJava.toCompletableFuture

  extension [T] (l: List[T]) def toJava: JList[T] = l.asJava
  
  extension [T] (l: JList[T]) def toScala: List[T] = l.asScala.toList
  
  extension [T] (s: JSet[T]) def toScala: Set[T] = s.asScala.toSet
  
  extension[T, U] (m: JMap[T, U]) def toScala: Map[T, U] = m.asScala.toMap
  
  extension [L, R] (either: Either[L, R]) def toJava: messages.Either[L, R] = either match
    case Left(leftValue) => messages.Either.forLeft(leftValue)
    case Right(rightValue) => messages.Either.forRight(rightValue) 

}

object ScalaJavaConverterAPI extends ScalaJavaConverter
