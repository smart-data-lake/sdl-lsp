package io.smartdatalake.utils

/** Thread-safe cache keeping the most recently used entries */
class LruCache[K, V](maxEntries: Int):
  private val entries = new java.util.LinkedHashMap[K, V](16, 0.75f, true):
    override def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean = this.size() > maxEntries

  def getOrCompute(key: K)(compute: => V): V = synchronized {
    Option(entries.get(key)).getOrElse {
      val value = compute
      entries.put(key, value)
      value
    }
  }

  def remove(key: K): Unit = synchronized { entries.remove(key) }

  def size: Int = synchronized { entries.size() }
