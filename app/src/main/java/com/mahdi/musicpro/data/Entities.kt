package com.mahdi.musicpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    indices = [Index(value = ["playlistId", "trackId"], unique = true)]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_edits")
data class TrackEditEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String
)

