package com.livingroomhq.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.livingroomhq.core.data.model.Channel

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val number: Int,
    val name: String,
    val groupName: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgChno: String? = null,
) {
    fun toModel(): Channel = Channel(
        id = id,
        number = number,
        name = name,
        group = groupName,
        streamUrl = streamUrl,
        logoUrl = logoUrl,
        isFavorite = isFavorite,
        tvgId = tvgId,
        tvgName = tvgName,
        tvgChno = tvgChno,
    )

    companion object {
        fun fromModel(model: Channel): ChannelEntity = ChannelEntity(
            id = model.id,
            number = model.number,
            name = model.name,
            groupName = model.group,
            streamUrl = model.streamUrl,
            logoUrl = model.logoUrl,
            isFavorite = model.isFavorite,
            tvgId = model.tvgId,
            tvgName = model.tvgName,
            tvgChno = model.tvgChno,
        )
    }
}
