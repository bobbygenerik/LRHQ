package com.livingroomhq.core.data.db

import androidx.room.Entity
import androidx.room.Index
import com.livingroomhq.core.data.model.Program

@Entity(
    tableName = "programs",
    primaryKeys = ["channelId", "startMillis"],
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["startMillis"]),
        Index(value = ["endMillis"]),
        Index(value = ["channelId", "endMillis", "startMillis"]),
        Index(value = ["endMillis", "startMillis"]),
        Index(value = ["sourceId"]),
    ],
)
data class ProgramEntity(
    val channelId: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val artworkUrl: String? = null,
    val sourceId: String = ChannelEntity.DEFAULT_SOURCE_ID,
) {
    fun toModel(): Program = Program(
        channelId = channelId,
        title = title,
        description = description,
        startMillis = startMillis,
        endMillis = endMillis,
        artworkUrl = artworkUrl,
    )

    companion object {
        fun fromModel(model: Program): ProgramEntity = ProgramEntity(
            channelId = model.channelId,
            title = model.title,
            description = model.description,
            startMillis = model.startMillis,
            endMillis = model.endMillis,
            artworkUrl = model.artworkUrl,
        )
    }
}

data class ProgramBrief(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    fun toModel(): Program = Program(
        channelId = channelId,
        title = title,
        description = "",
        startMillis = startMillis,
        endMillis = endMillis,
        artworkUrl = null,
    )
}
