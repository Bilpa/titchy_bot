package model

import com.google.gson.annotations.SerializedName


data class CurrentlyPlaying(
    @SerializedName("item") val item: Track?
)
