package com.livingroomhq.player

import android.content.Context
import android.content.Intent
import android.util.Log
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.EpisodeItem
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodItem
import com.livingroomhq.ui.UiMessages

object ChannelPlayer {
    const val EXTRA_CHANNEL_ID = "channel_id"
    const val EXTRA_STREAM_URL = "stream_url"
    const val EXTRA_CHANNEL_NAME = "channel_name"
    const val EXTRA_IS_VOD = "is_vod"
    const val EXTRA_VOD_YEAR = "vod_year"
    const val EXTRA_VOD_RATING = "vod_rating"
    const val EXTRA_VOD_GENRE = "vod_genre"
    const val EXTRA_VOD_DESCRIPTION = "vod_description"
    const val EXTRA_VOD_POSTER_URL = "vod_poster_url"

    fun launch(context: Context, channel: Channel) {
        if (channel.streamUrl.isBlank()) return
        context.startActivity(
            Intent(context, ChannelPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channel.id)
                putExtra(EXTRA_STREAM_URL, channel.streamUrl)
                putExtra(EXTRA_CHANNEL_NAME, channel.name)
                putExtra(EXTRA_IS_VOD, false)
            },
        )
    }

    fun launchVod(context: Context, item: VodItem) {
        if (item.streamUrl.isBlank()) {
            Log.w("ChannelPlayer", "launchVod: streamUrl is blank for '${item.title}'")
            UiMessages.post("No stream URL available for ${item.title}")
            return
        }
        Log.i("ChannelPlayer", "launchVod: '${item.title}' -> ${item.streamUrl}")
        context.startActivity(
            Intent(context, ChannelPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, item.id)
                putExtra(EXTRA_STREAM_URL, item.streamUrl)
                putExtra(EXTRA_CHANNEL_NAME, item.title)
                putExtra(EXTRA_IS_VOD, true)
                putExtra(EXTRA_VOD_YEAR, item.year)
                putExtra(EXTRA_VOD_RATING, item.rating)
                putExtra(EXTRA_VOD_GENRE, item.genre)
                putExtra(EXTRA_VOD_DESCRIPTION, item.description)
                putExtra(EXTRA_VOD_POSTER_URL, item.posterUrl)
            },
        )
    }

    fun launchEpisode(
        context: Context,
        series: SeriesItem,
        episode: EpisodeItem,
    ) {
        if (episode.streamUrl.isBlank()) {
            Log.w("ChannelPlayer", "launchEpisode: streamUrl is blank for '${episode.title}'")
            UiMessages.post("No stream URL available for this episode")
            return
        }
        val displayTitle = if (episode.title.isNotBlank() && !episode.title.startsWith("Episode", ignoreCase = true)) {
            "${series.title} • S%02dE%02d • ${episode.title}".format(episode.seasonNum, episode.episodeNum)
        } else {
            "${series.title} • S%02dE%02d".format(episode.seasonNum, episode.episodeNum)
        }
        Log.i("ChannelPlayer", "launchEpisode: '$displayTitle' -> ${episode.streamUrl}")
        context.startActivity(
            Intent(context, ChannelPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, episode.id)
                putExtra(EXTRA_STREAM_URL, episode.streamUrl)
                putExtra(EXTRA_CHANNEL_NAME, displayTitle)
                putExtra(EXTRA_IS_VOD, true)
                putExtra(EXTRA_VOD_YEAR, series.year)
                putExtra(EXTRA_VOD_RATING, series.rating)
                putExtra(EXTRA_VOD_GENRE, series.genre.ifBlank { series.categoryName })
                putExtra(EXTRA_VOD_DESCRIPTION, episode.plot.ifBlank { series.plot })
                putExtra(EXTRA_VOD_POSTER_URL, episode.posterUrl ?: series.posterUrl)
            },
        )
    }
}
