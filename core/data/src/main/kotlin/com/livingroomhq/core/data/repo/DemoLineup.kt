package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program

/** Bundled demo lineup + EPG so the launcher is navigable with no playlist configured. */
object DemoLineup {

    fun channels(): List<Channel> {
        val groups = listOf("News", "Sports", "Movies", "Kids", "Music")
        val names = listOf(
            "Atlas News", "World 24", "Court Center", "Arena One", "Prime Field",
            "Cinema Gold", "Noir Classics", "Tiny Planet", "Cartoon Bay", "Wave FM",
            "Symphony HD", "Docu Sphere", "Metro Live", "Night Owl", "Galaxy Sports",
        )
        return names.mapIndexed { i, name ->
            Channel(
                id = "ch-$i",
                number = i + 1,
                name = name,
                group = groups[i % groups.size],
                streamUrl = "https://demo.livingroomhq.local/streams/${i + 1}.m3u8",
                isFavorite = i % 4 == 0,
            )
        }
    }

    fun epg(): Map<String, List<Program>> {
        val now = System.currentTimeMillis()
        val half = 30 * 60 * 1000L
        val shows = listOf(
            "Evening Report" to "The day's stories with in-depth analysis.",
            "Championship Live" to "Live coverage from the arena floor.",
            "Golden Age Cinema" to "A restored classic from the studio vaults.",
            "Deep Blue" to "Documentary diving beneath the polar ice.",
            "Late Session" to "Unplugged performances after dark.",
        )
        return channels().associate { channel ->
            val (title, desc) = shows[channel.number % shows.size]
            channel.id to listOf(
                Program(channel.id, title, desc, now - half, now + half),
                Program(channel.id, "$title: Next Hour", desc, now + half, now + 3 * half),
            )
        }
    }
}
