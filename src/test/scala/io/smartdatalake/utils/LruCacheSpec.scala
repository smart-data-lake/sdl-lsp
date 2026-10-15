package io.smartdatalake.utils

import io.smartdatalake.UnitSpec

class LruCacheSpec extends UnitSpec:

    "LRU cache" should "compute a value only once and evict the least recently used entries" in {
        val cache = LruCache[String, Int](maxEntries = 2)
        var computations = 0
        def compute(value: Int): Int = { computations += 1; value }

        cache.getOrCompute("a")(compute(1)) shouldBe 1
        cache.getOrCompute("a")(compute(99)) shouldBe 1
        computations shouldBe 1

        cache.getOrCompute("b")(compute(2))
        cache.getOrCompute("a")(compute(99)) // a is now the most recently used
        cache.getOrCompute("c")(compute(3))  // evicts b
        cache.size shouldBe 2
        cache.getOrCompute("b")(compute(20)) shouldBe 20
        cache.getOrCompute("c")(compute(99)) shouldBe 3
    }

    it should "remove entries" in {
        val cache = LruCache[String, Int](maxEntries = 2)
        cache.getOrCompute("a")(1)
        cache.remove("a")
        cache.getOrCompute("a")(2) shouldBe 2
    }
