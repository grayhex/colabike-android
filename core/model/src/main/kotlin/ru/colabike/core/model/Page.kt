package ru.colabike.core.model

/** One page of a keyset list; [nextCursor] is opaque and null on the last page. */
data class Page<T>(val items: List<T>, val nextCursor: String?) {
    val hasMore: Boolean
        get() = nextCursor != null
}
