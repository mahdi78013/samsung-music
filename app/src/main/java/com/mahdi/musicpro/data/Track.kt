package com.mahdi.musicpro.data

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val genre: String,
    val coverGradientStart: String, // Hex string for visual gradient
    val coverGradientEnd: String,   // Hex string for visual gradient
    val lyrics: List<LyricLine> = emptyList(),
    val albumArtUri: String? = null,
    val dateAddedSecs: Long = 0L
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String = ""
)

object TrackLibrary {
    val demoTracks = listOf(
        Track(
            id = "demo_1",
            title = "Synthwave Horizon",
            artist = "Neon Dreamer",
            album = "Cosmic Slate",
            durationMs = 372000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            genre = "Synthwave",
            coverGradientStart = "#FF416C",
            coverGradientEnd = "#FF4B2B",
            lyrics = listOf(
                LyricLine(0L, "Welcome to the Synthwave Horizon..."),
                LyricLine(10000L, "Feel the rhythm of the neon lights..."),
                LyricLine(20000L, "Sailing under a retro twilight..."),
                LyricLine(30000L, "Listen to the cosmic frequency..."),
                LyricLine(40000L, "Enjoy the HIFI smart core engine..."),
                LyricLine(50000L, "Pure musical bliss, synthetic perfection.")
            )
        ),
        Track(
            id = "demo_2",
            title = "Sunset Chillout Journey",
            artist = "Acoustic Aura",
            album = "Serene Escapes",
            durationMs = 423000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            genre = "Ambient",
            coverGradientStart = "#1F1C2C",
            coverGradientEnd = "#928DAB",
            lyrics = listOf(
                LyricLine(0L, "Sunset Chillout Journey starts now..."),
                LyricLine(15000L, "Unwind your mind, let the stress fade..."),
                LyricLine(30000L, "Breathe in, breathe out..."),
                LyricLine(45000L, "Enjoying the acoustic soundscapes..."),
                LyricLine(60000L, "All rights reserved.")
            )
        ),
        Track(
            id = "demo_3",
            title = "High-Fi Electronic Groove",
            artist = "The Digital Tribe",
            album = "Hyper Quantum",
            durationMs = 344000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            genre = "Electronic",
            coverGradientStart = "#11998e",
            coverGradientEnd = "#38ef7d",
            lyrics = listOf(
                LyricLine(0L, "High-Fi Electronic Groove..."),
                LyricLine(12000L, "Cybernetic frequencies pulsing..."),
                LyricLine(24000L, "Dance into a hyper quantum reality..."),
                LyricLine(36000L, "Feel the digital heat!"),
                LyricLine(48000L, "Maximum acoustic performance.")
            )
        ),
        Track(
            id = "demo_4",
            title = "Deep Lounge Ambient",
            artist = "Lounge Core",
            album = "Midnight Senses",
            durationMs = 302000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            genre = "Lounge",
            coverGradientStart = "#FC466B",
            coverGradientEnd = "#3F5EFB",
            lyrics = listOf(
                LyricLine(0L, "Deep Lounge Ambient chill..."),
                LyricLine(10000L, "Sipping tea in the midnight rain..."),
                LyricLine(20000L, "A smooth atmosphere surrounds us..."),
                LyricLine(30000L, "Tuning into the calm frequencies..."),
                LyricLine(40000L, "Rest and recover.")
            )
        ),
        Track(
            id = "demo_5",
            title = "HIFI Heavy Bass Test",
            artist = "Subwoofer Labs",
            album = "Audio Waves",
            durationMs = 362000L,
            contentUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            genre = "Bass / Techno",
            coverGradientStart = "#00c6ff",
            coverGradientEnd = "#0072ff",
            lyrics = listOf(
                LyricLine(0L, "Subwoofer Sub-bass check..."),
                LyricLine(10000L, "30Hz, 40Hz, 50Hz sweep..."),
                LyricLine(20000L, "Equalizer check complete..."),
                LyricLine(30000L, "Testing UHQ upscaler capabilities..."),
                LyricLine(40000L, "Dynamic bass boost active.")
            )
        )
    )

    var originalTracks: List<Track> = demoTracks
    var tracks: List<Track> = demoTracks

    fun updateTrack(id: String, title: String, artist: String, album: String) {
        tracks = tracks.map {
            if (it.id == id) {
                it.copy(title = title, artist = artist, album = album)
            } else {
                it
            }
        }
    }
}
