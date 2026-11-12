package com.mushaf.reader.reader

/**
 * The sources the explanation sheet can show, under the ids the store remembers them by: the app's
 * own tafsirs, the word meanings, and each downloaded pack as `pack:<id>`.
 *
 * The reader pins up to [MAX_PINNED] of them: those are the pages the sheet swipes between. Every
 * source, pinned or not, is in the sheet's sources menu. The sheet opens on the source read last.
 */
object ExplanationSources {
    const val SAADI = "saadi"
    const val MUKHTASAR = "mukhtasar"
    const val MUYASSAR = "muyassar"
    const val MEANINGS = "meanings"
    private const val PACK_PREFIX = "pack:"

    /** The sheet's own sources, in the order the sources menu lists them. */
    val BUILT_IN = listOf(SAADI, MUKHTASAR, MUYASSAR, MEANINGS)

    /** The pins before the reader changes them: the sheet's own four. */
    val DEFAULT_PINNED = BUILT_IN

    /** More pages than this is a long way to swipe; the rest stay one tap away in the menu. */
    const val MAX_PINNED = 4

    fun pack(packId: String): String = PACK_PREFIX + packId

    /** The pack a source shows, or null for one of the sheet's own sources. */
    fun packId(source: String): String? = source.takeIf { it.startsWith(PACK_PREFIX) }?.removePrefix(PACK_PREFIX)

    /** Whether [source] can be shown now: one of the sheet's own, or a pack still on the device. */
    fun isAvailable(source: String, installedPacks: Set<String>): Boolean =
        packId(source)?.let { it in installedPacks } ?: (source in BUILT_IN)

    /** The source the sheet opens on: the last one read, unless its pack has been deleted since. */
    fun opening(last: String, installedPacks: Set<String>): String =
        if (isAvailable(last, installedPacks)) last else SAADI

    /**
     * The last source as versions before 0.15 remembered it: a tafsir, or «more» meaning whichever
     * pack was chosen in the old «المزيد» tab.
     */
    fun fromLegacy(tafsir: String?, pack: String?): String = when (tafsir) {
        SAADI, MUKHTASAR, MUYASSAR -> tafsir
        "more" -> pack?.takeIf { it.isNotBlank() }?.let(::pack) ?: SAADI
        else -> SAADI
    }

    /** The stored pins, in order; nothing stored yet means the defaults. */
    fun parsePinned(raw: String?): List<String> {
        if (raw == null) return DEFAULT_PINNED
        return raw.split(',')
            .map { it.trim() }
            .filter { it in BUILT_IN || (packId(it)?.isNotBlank() == true) }
            .distinct()
            .take(MAX_PINNED)
    }

    fun encodePinned(pinned: List<String>): String = pinned.joinToString(",")

    /**
     * [source] pinned or unpinned. A new pin goes after the others; null when all [MAX_PINNED] places
     * are taken, so the caller can say so instead of silently dropping one.
     */
    fun togglePin(pinned: List<String>, source: String): List<String>? = when {
        source in pinned -> pinned - source
        pinned.size >= MAX_PINNED -> null
        else -> pinned + source
    }

    /** The pins that can be shown now: a pin on a deleted pack stays stored but is hidden. */
    fun visiblePinned(pinned: List<String>, installedPacks: Set<String>): List<String> =
        pinned.filter { isAvailable(it, installedPacks) }
}
