package com.livingroomhq.core.data.model

data class VodItem(
    val id: String,
    val title: String,
    val description: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int = 0,
    val rating: String? = null,
    val genre: String = "General",
    val durationMinutes: Int = 0,
    val streamUrl: String,
)

data class VodCategory(
    val id: String,
    val name: String,
    val items: List<VodItem>,
)

data class SeriesItem(
    val id: String,
    val seriesId: Int,
    val title: String,
    val plot: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int = 0,
    val rating: String? = null,
    val genre: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
)

data class EpisodeItem(
    val id: String,
    val episodeNum: Int,
    val seasonNum: Int,
    val title: String,
    val plot: String = "",
    val posterUrl: String? = null,
    val durationMinutes: Int = 0,
    val streamUrl: String,
)

data class SeriesDetail(
    val series: SeriesItem,
    val seasons: List<Int>,
    val episodesBySeason: Map<Int, List<EpisodeItem>>,
)

data class SeriesCategory(
    val id: String,
    val name: String,
    val series: List<SeriesItem>,
)
