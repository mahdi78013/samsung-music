package com.mahdi.musicpro.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun deleteFavoriteByTrackId(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId LIMIT 1)")
    suspend fun isFavoriteSync(trackId: String): Boolean

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY displayOrder ASC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylists(playlists: List<PlaylistEntity>)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Int, newName: String)

    @Query("UPDATE playlists SET displayOrder = :displayOrder WHERE id = :id")
    suspend fun updatePlaylistOrder(id: Int, displayOrder: Int)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Int)

    // Playlist Tracks
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getTracksForPlaylist(playlistId: Int): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrack(playlistId: Int, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllTracksForPlaylist(playlistId: Int)

    // Recent History
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAt DESC LIMIT 50)")
    suspend fun pruneHistory()

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // Track Edits
    @Query("SELECT * FROM track_edits")
    fun getAllTrackEdits(): Flow<List<TrackEditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackEdit(edit: TrackEditEntity)
}

@Database(
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, HistoryEntity::class, TrackEditEntity::class],
    version = 5,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_pro_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
