package network

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class SpotifyTrack(
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<SpotifyArtist>
)

data class SpotifyArtist(
    @SerializedName("name") val name: String
)

data class CurrentlyPlaying(
    @SerializedName("item") val item: SpotifyTrack?
)

object SpotifyApi {
    private const val BASE_URL = "https://api.spotify.com/v1/"

    private val gson = GsonBuilder()
        .serializeNulls()
        .create()

    val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SpotifyApiService::class.java)
    }
}
