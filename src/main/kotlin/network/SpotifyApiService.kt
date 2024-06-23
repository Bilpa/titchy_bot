package network

import model.SpotifyUser
import retrofit2.http.GET
import retrofit2.http.Header

interface SpotifyApiService {
    @GET("me")
    suspend fun getCurrentUserProfile(@Header("Authorization") authHeader: String): SpotifyUser
}