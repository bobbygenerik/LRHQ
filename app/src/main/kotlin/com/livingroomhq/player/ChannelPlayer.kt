package com.livingroomhq.player

import android.content.Context
import android.content.Intent
import com.livingroomhq.core.data.model.Channel

object ChannelPlayer {
    const val EXTRA_CHANNEL_ID = "channel_id"

    fun launch(context: Context, channel: Channel) {
        if (channel.streamUrl.isBlank()) return
        context.startActivity(
            Intent(context, ChannelPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channel.id)
            },
        )
    }
}
