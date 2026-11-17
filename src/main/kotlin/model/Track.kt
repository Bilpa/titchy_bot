package model

import com.google.gson.annotations.SerializedName

data class Track(
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<Artist>,
    val uri: String
)

data class Tracks(
    val items: List<Track>
)
