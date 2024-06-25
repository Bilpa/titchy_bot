package model



data class UserQueue(
    val currently_playing: CurrentlyPlaying?,
    val queue: List<Track>
)
