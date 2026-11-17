package network

import model.CurrentlyPlaying
import model.SearchResult
import model.SpotifyUser
import model.UserQueue
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query


interface SpotifyApiService {
    @GET("me")
    suspend fun getCurrentUserProfile(@Header("Authorization") authHeader: String): SpotifyUser

    @GET("me/player/currently-playing")
    suspend fun getCurrentlyPlaying(@Header("Authorization") authHeader: String): Response<CurrentlyPlaying?>

    @GET("me/player/queue")
    suspend fun getUserQueue(@Header("Authorization") authHeader: String): Response<UserQueue?>

    @POST("me/player/queue")
    suspend fun addSongToQueue(
        @Header("Authorization") authHeader: String,
        @Query("uri") uri:String
    ): Response<Unit>

    @GET("search")
    suspend fun searchSong(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 1
    ): Response<SearchResult>

}