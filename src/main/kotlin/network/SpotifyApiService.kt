package network

import model.SpotifyUser
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface SpotifyApiService {
    @GET("me")
    suspend fun getCurrentUserProfile(@Header("Authorization") authHeader: String): SpotifyUser

    @GET("me/player/currently-playing")
    suspend fun getCurrentlyPlaying(@Header("Authorization") authHeader: String): Response<CurrentlyPlaying?>
}