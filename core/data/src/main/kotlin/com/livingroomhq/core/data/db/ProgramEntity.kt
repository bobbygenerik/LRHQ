package com.livingroomhq.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.livingroomhq.core.data.model.Program

@Entity(
    tableName = "programs",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["startMillis"]),
        Index(value = ["endMillis"])
    ]
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val dbId: Long = 0,
    val channelId: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val artworkUrl: String? = null,
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
