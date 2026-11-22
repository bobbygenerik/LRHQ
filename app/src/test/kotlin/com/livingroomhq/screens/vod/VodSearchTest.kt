package com.livingroomhq.screens.vod

import com.livingroomhq.core.data.model.SeriesCategory
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodCategory
import com.livingroomhq.core.data.model.VodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VodSearchTest {
    private fun movie(id: String, title: String) = VodItem(id = id, title = title, streamUrl = "x")
    private fun show(id: String, title: String) = SeriesItem(id = id, seriesId = id.hashCode(), title = title)

    private val movies = listOf(
        VodCategory(
            "a", "Action",
            listOf(movie("1", "The Matrix"), movie("2", "Matrix Reloaded"), movie("3", "Die Hard")),
        ),
        // Same title listed again under another category.
        VodCategory("b", "Sci-Fi", listOf(movie("1", "The Matrix"), movie("4", "PT: A Matriz 4K"))),
    )
    private val series = listOf(
        SeriesCategory("s", "Drama", listOf(show("10", "Pátio das Cantigas"), show("11", "Breaking Bad"))),
    )

    @Test fun `short queries return nothing`() {
        val r = VodSearch.search("m", movies, series)
        assertTrue(r.movies.isEmpty() && r.series.isEmpty())
    }

    @Test fun `dedupes by id and ranks prefix matches first`() {
        val r = VodSearch.search("matrix", movies, series)
        assertEquals(listOf("2", "1"), r.movies.map { it.id })
    }

    @Test fun `all words must match in any order`() {
        assertEquals(listOf("1"), VodSearch.search("matrix the", movies, series).movies.map { it.id })
        assertTrue(VodSearch.search("matrix hard", movies, series).movies.isEmpty())
    }

    @Test fun `accent and case insensitive`() {
        assertEquals(listOf("10"), VodSearch.search("PATIO", movies, series).series.map { it.id })
    }

    @Test fun `country and quality tags are ignored`() {
        assertEquals(listOf("4"), VodSearch.search("a matriz", movies, series).movies.map { it.id })
        assertTrue(VodSearch.search("4k", movies, series).movies.isEmpty())
    }
}
