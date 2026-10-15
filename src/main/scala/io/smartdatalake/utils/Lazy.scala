package io.smartdatalake.utils

/** Value computed on first access, shared by everything holding this instance: unlike a lazy val, it survives the copies of a case class */
final class Lazy[T](compute: => T):
  lazy val value: T = compute
