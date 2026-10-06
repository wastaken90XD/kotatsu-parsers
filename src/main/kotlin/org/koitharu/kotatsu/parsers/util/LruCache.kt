package org.koitharu.kotatsu.parsers.util

import java.util.LinkedHashMap

/** Thread-safe, access-ordered cache with a strict maximum entry count. */
internal class LruCache<K, V>(private val maxEntries: Int) {

	private val entries = object : LinkedHashMap<K, V>(maxEntries + 1, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
	}

	init {
		require(maxEntries > 0) { "maxEntries must be positive" }
	}

	@Synchronized
	operator fun get(key: K): V? = entries[key]

	@Synchronized
	operator fun set(key: K, value: V) {
		entries[key] = value
	}
}
