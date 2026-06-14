package com.livingroomhq.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    @Query("SELECT * FROM channels ORDER BY number ASC")
    fun getChannelsFlow(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY number ASC")
    suspend fun getChannels(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearChannels()

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateChannelFavorite(channelId: String, isFavorite: Boolean)

    @Query("SELECT * FROM programs WHERE channelId = :channelId ORDER BY startMillis ASC")
    suspend fun getProgramsForChannel(channelId: String): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE endMillis > :now")
    suspend fun getActivePrograms(now: Long): List<ProgramEntity>

    @Query(
        """
        SELECT * FROM programs
        WHERE endMillis > :now AND startMillis < :windowEnd
        ORDER BY startMillis ASC
        """,
    )
    suspend fun getProgramsInWindow(now: Long, windowEnd: Long): List<ProgramEntity>

    @Query(
        """
        SELECT * FROM programs
        WHERE channelId = :channelId AND endMillis > :now AND startMillis < :windowEnd
        ORDER BY startMillis ASC
        """,
    )
    suspend fun getProgramsForChannelInWindow(
        channelId: String,
        now: Long,
        windowEnd: Long,
    ): List<ProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs")
    suspend fun clearPrograms()

    @Query("DELETE FROM programs WHERE channelId = :channelId")
    suspend fun deleteProgramsForChannel(channelId: String)

    @Transaction
    suspend fun replaceChannels(channels: List<ChannelEntity>) {
        clearChannels()
        insertChannels(channels)
    }

    @Transaction
    suspend fun replacePrograms(programs: List<ProgramEntity>) {
        clearPrograms()
        insertPrograms(programs)
    }

    @Query("SELECT * FROM guide_channels")
    suspend fun getAllGuideChannels(): List<GuideChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuideChannels(channels: List<GuideChannelEntity>)

    @Query("DELETE FROM guide_channels")
    suspend fun clearGuideChannels()

    @Transaction
    suspend fun replaceGuideChannels(channels: List<GuideChannelEntity>) {
        clearGuideChannels()
        insertGuideChannels(channels)
    }
}
