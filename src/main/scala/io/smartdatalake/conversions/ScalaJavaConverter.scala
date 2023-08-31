package io.smartdatalake.conversions

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.messages
import scala.concurrent.Future
import scala.jdk.FutureConverters.*
import scala.jdk.CollectionConverters.*

trait ScalaJavaConverter {

  extension [T] (f: Future[T]) def toJava: CompletableFuture[T] = f.asJava.toCompletableFuture

  extension [T] (l: List[T]) def toJava: java.util.List[T] = l.asJava
  
  extension [L, R] (either: Either[L, R]) def toJava: messages.Either[L, R] = either match
    case Left(leftValue) => messages.Either.forLeft(leftValue)
    case Right(rightValue) => messages.Either.forRight(rightValue) 

}

object ScalaJavaConverterAPI extends ScalaJavaConverter
