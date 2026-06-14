package com.livingroomhq.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** XMLTV `<channel>` metadata used to match M3U lineup entries to guide programmes. */
@Entity(tableName = "guide_channels")
data class GuideChannelEntity(
    @PrimaryKey val id: String,
    /** Pipe-separated display names from the guide. */
    val displayNames: String,
) {
    fun displayNameList(): List<String> =
        if (displayNames.isEmpty()) emptyList() else displayNames.split('|')

    companion object {
        fun fromAliases(id: String, names: Collection<String>): GuideChannelEntity =
            GuideChannelEntity(
                id = id,
                displayNames = names.joinToString("|"),
            )
    }
}
