package com.mahdi.musicpro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class MusicRepository(private val musicDao: MusicDao) {

    val deviceTracksFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Track>>(emptyList())

    val allTracksFlow: Flow<List<Track>> = combine(deviceTracksFlow, musicDao.getAllTrackEdits()) { deviceTracks, edits ->
        val baseTracks = if (deviceTracks.isEmpty()) {
            TrackLibrary.demoTracks
        } else {
            deviceTracks
        }.distinctBy { it.id }
        val editMap = edits.associateBy { it.id }
        val updated = baseTracks.map { track ->
            val edit = editMap[track.id]
            if (edit != null) {
                track.copy(title = edit.title, artist = edit.artist, album = edit.album)
            } else {
                track
            }
        }
        // Cache in memory fallback
        TrackLibrary.originalTracks = baseTracks
        TrackLibrary.tracks = updated
        updated
    }.distinctUntilChanged()

    val favoritesFlow: Flow<List<Track>> = combine(allTracksFlow, musicDao.getAllFavorites()) { tracks, favList ->
        val favIds = favList.map { it.trackId }.toSet()
        tracks.filter { it.id in favIds }
    }.distinctUntilChanged()

    suspend fun isFavorite(trackId: String): Boolean {
        return musicDao.isFavoriteSync(trackId)
    }

    suspend fun toggleFavorite(trackId: String) {
        val currentlyFav = musicDao.isFavoriteSync(trackId)
        if (currentlyFav) {
            musicDao.deleteFavoriteByTrackId(trackId)
        } else {
            musicDao.insertFavorite(FavoriteEntity(trackId))
        }
    }

    val playlistsFlow: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists().distinctUntilChanged()

    val allPlaylistTracksFlow: Flow<List<PlaylistTrackEntity>> = musicDao.getAllPlaylistTracks().distinctUntilChanged()

    fun getTracksForPlaylist(playlistId: Int): Flow<List<Track>> {
        return combine(allTracksFlow, musicDao.getTracksForPlaylist(playlistId)) { tracks, crossRefs ->
            crossRefs.mapNotNull { ref ->
                tracks.find { it.id == ref.trackId }
            }
        }
    }

    suspend fun createPlaylist(name: String) {
        if (name.isNotBlank()) {
            musicDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    suspend fun renamePlaylist(playlistId: Int, newName: String) {
        if (newName.isNotBlank()) {
            musicDao.renamePlaylist(playlistId, newName)
        }
    }

    suspend fun updatePlaylistOrder(playlistId: Int, displayOrder: Int) {
        musicDao.updatePlaylistOrder(playlistId, displayOrder)
    }

    suspend fun deletePlaylist(playlistId: Int) {
        musicDao.deletePlaylistById(playlistId)
        musicDao.deleteAllTracksForPlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, trackId: String) {
        musicDao.insertPlaylistTrack(PlaylistTrackEntity(playlistId = playlistId, trackId = trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        musicDao.deletePlaylistTrack(playlistId, trackId)
    }

    val recentTracksFlow: Flow<List<Track>> = combine(allTracksFlow, musicDao.getRecentHistory()) { tracks, historyList ->
        // Return tracks in order of historical play, allowing sequential duplicates properly
        historyList.mapNotNull { history ->
            tracks.find { it.id == history.trackId }
        }
    }.distinctUntilChanged()

    suspend fun markTrackPlayed(trackId: String) {
        musicDao.insertHistory(HistoryEntity(trackId = trackId))
        musicDao.pruneHistory()
    }

    suspend fun saveTrackEdit(trackId: String, title: String, artist: String, album: String) {
        musicDao.insertTrackEdit(TrackEditEntity(id = trackId, title = title, artist = artist, album = album))
    }

    suspend fun clearHistory() {
        musicDao.clearHistory()
    }
}
