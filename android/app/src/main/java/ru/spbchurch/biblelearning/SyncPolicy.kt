package ru.spbchurch.biblelearning

/** Pure three-way merge. Empty/missing values represent cleared website answers. */
object SyncPolicy {
    fun normalize(values: Map<String, String>) = values.filterValues { it.isNotEmpty() }
    data class Merge(val answers: Map<String, String>, val conflicts: Set<String>)
    fun merge(base: Map<String, String>, local: Map<String, String>, remote: Map<String, String>): Merge {
        val result = mutableMapOf<String, String>()
        val conflicts = mutableSetOf<String>()
        (base.keys + local.keys + remote.keys).forEach { key ->
            val b = base[key].orEmpty()
            val l = local[key].orEmpty()
            val r = remote[key].orEmpty()
            val value = when {
                l == r -> l
                l == b -> r
                r == b -> l
                else -> { conflicts.add(key); l }
            }
            if (value.isNotEmpty()) result[key] = value
        }
        return Merge(result, conflicts)
    }
}
