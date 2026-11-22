package com.livingroomhq.screens.vod

import com.livingroomhq.core.data.model.DisplayNames
import com.livingroomhq.core.data.model.SeriesCategory
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodCategory
import com.livingroomhq.core.data.model.VodItem
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Title search across the whole VOD catalog. Pure and synchronous so it can run
 * on a background dispatcher and be unit-tested without Android.
 *
 * Matching runs on the display title (country/quality tags stripped), so
 * "PT: " or " 4K" never decide a match. Every query word must appear in it (case- and accent-
 * insensitive, punctuation ignored). Results are de-duplicated by id because
 * providers list the same title under several categories, then ranked:
 * exact title > title starts with query > a word starts with query > contains.
 */
object VodSearch {

    const val MIN_QUERY_LENGTH = 2
    const val MAX_RESULTS = 300

    data class Results(
        val query: String,
        val movies: List<VodItem>,
        val series: List<SeriesItem>,
    )

    private val diacritics = Regex("""\p{Mn}+""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    /** Normalized display titles; catalogs are large and titles repeat across searches. */
    private val titleKeys = ConcurrentHashMap<String, String>()

    private fun titleKey(raw: String): String =
        titleKeys.getOrPut(raw) { normalize(DisplayNames.title(raw)) }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
            .replace(nonWord, " ")
            .trim()

    fun search(
        query: String,
        movieCategories: List<VodCategory>,
        seriesCategories: List<SeriesCategory>,
    ): Results {
        val q = normalize(query)
        if (q.length < MIN_QUERY_LENGTH) return Results(query, emptyList(), emptyList())
        val tokens = q.split(' ').filter { it.isNotEmpty() }

        val movies = rank(
            items = movieCategories.asSequence().flatMap { it.items.asSequence() },
            id = { it.id },
            title = { it.title },
            q = q,
            tokens = tokens,
        )
        val series = rank(
            items = seriesCategories.asSequence().flatMap { it.series.asSequence() },
            id = { it.id },
            title = { it.title },
            q = q,
            tokens = tokens,
        )
        return Results(query, movies, series)
    }

    private inline fun <T> rank(
        items: Sequence<T>,
        id: (T) -> String,
        title: (T) -> String,
        q: String,
        tokens: List<String>,
    ): List<T> {
        val seen = HashSet<String>()
        val scored = ArrayList<Pair<Int, T>>()
        for (item in items) {
            if (!seen.add(id(item))) continue
            val t = titleKey(title(item))
            if (tokens.any { !t.contains(it) }) continue
            val score = when {
                t == q -> 0
                t.startsWith(q) -> 1
                t.contains(" $q") -> 2
                else -> 3
            }
            scored += score to item
        }
        // sortedBy is stable, so provider order is kept within a score bucket.
        return scored.sortedBy { it.first }.take(MAX_RESULTS).map { it.second }
    }
}
