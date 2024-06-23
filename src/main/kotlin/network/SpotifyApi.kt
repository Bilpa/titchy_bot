package network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SpotifyApi {
    private const val BASE_URL = "https://api.spotify.com/v1/"

    val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }
}
