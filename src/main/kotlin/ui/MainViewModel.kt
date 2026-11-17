package ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.*
import model.*

import network.SpotifyApi
import network.SpotifyApiService
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import util.AuthUtil
import util.Env

/**
 * Dependencies are constructor parameters so tests can substitute fakes; the defaults are the
 * production wiring. [refreshSpotifyToken] is a lambda so that constructing the ViewModel does not
 * initialize [AuthUtil] (which starts the OAuth callback server on port 8080).
 */
class MainViewModel(
    private val spotifyApi: SpotifyApiService = SpotifyApi.apiService,
    private val refreshSpotifyToken: () -> String = {
        AuthUtil.refreshSpotifyToken(Env.spotifyClientId, Env.spotifyClientSecret)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sendChatMessage: (String) -> Unit = { AuthUtil.sendMessageToTwitchChat(it) },
) : ViewModel() {
    var spotifyToken by mutableStateOf<String?>(null)
    var twitchToken by mutableStateOf<String?>(null)
    var userProfile by mutableStateOf<SpotifyUser?>(null)
    var currentlyPlaying by mutableStateOf<CurrentlyPlaying?>(null)
    var nextInQueue by mutableStateOf<Track?>(null)
    var responseMessage by mutableStateOf<String?>(null)
    var recentlyAddedTrack by mutableStateOf<Track?>(null)
    var currentQueue by mutableStateOf<String?>(null)

    private var pollingJob: Job? = null

    fun authenticateWithSpotify() {
        AuthUtil.authenticate(Env.spotifyClientId, Env.spotifyClientSecret, "spotify") { token ->
            onSpotifyAuthenticated(token)
        }
    }

    internal fun onSpotifyAuthenticated(token: String) {
        spotifyToken = token
        loadUserProfile(token)
        startPollingCurrentlyPlaying(token)
        println("spotifyToken = ${spotifyToken}")
    }

    fun authenticateWithTwitch(){
        AuthUtil.authenticate(Env.twitchClientId, Env.twitchClientSecret, "twitch") { token ->
            twitchToken = token
            println("twitchToken = ${twitchToken}")
            connectToTwitchChat()

        }
    }



    private fun startPollingCurrentlyPlaying(token: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(ioDispatcher) {
            while (isActive){
                loadCurrentlyPlaying(spotifyToken!!)
                delay(5000)
            }
        }
    }

    private fun loadUserProfile(token: String) {
        viewModelScope.launch {
            val authHeader = "Bearer $token"
            try {
                val profile = spotifyApi.getCurrentUserProfile(authHeader)
                userProfile = profile
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    refreshTokenAndRetry { loadUserProfile(it) }
                } else {

                    e.printStackTrace()
                    userProfile = null
                }
            }
        }
    }

    private fun loadCurrentlyPlaying(token: String){

        viewModelScope.launch(Dispatchers.Main) {
            val authHeader = "Bearer $token"
            try {
                val response: Response<CurrentlyPlaying?> = spotifyApi.getCurrentlyPlaying(authHeader)

                if (response.isSuccessful){
                    currentlyPlaying = response.body()
                    println("load currently playing...")
                    println("response = ${response.body()}")
                    loadUserQueue(token)
                }
                else {
                    refreshTokenAndRetry { loadCurrentlyPlaying(it) }
                    println("on respons fail: response = ${response.message()}")
                    currentlyPlaying = null
                }


            } catch (e: HttpException){
                if (e.code() == 401) {
                    refreshTokenAndRetry { loadCurrentlyPlaying(it) }
                }else {

                    e.printStackTrace()
                    currentlyPlaying = null
                }
            }
        }
    }

    private fun loadUserQueue(token: String) {
        println("load user queue...")
        viewModelScope.launch(Dispatchers.Main) {
            val authHeader = "Bearer $token"
            try {

                val response: Response<UserQueue?> = spotifyApi.getUserQueue(authHeader)
                println("response = ${response.body()}")
                if (response.isSuccessful){
                    nextInQueue = response.body()?.queue?.firstOrNull()
                    currentQueue = "current queue is: "
                    var count = 1
                    response.body()?.queue?.forEach { track ->
                        currentQueue += "$count. ${track.name} by ${track.artists.get(0).name} "
                        count++
                    }
                    println("queue = ${currentQueue}")
                } else{
                    currentQueue = "failed to load queue"
                    println("on response fail: response.Message = ${response.message()}")

                    nextInQueue = null
                }
            } catch (e:HttpException){
                if (e.code() == 401) {
                    println("load user queue error 401")
                    println("e.message() = ${e.message()}")
                    refreshTokenAndRetry { newToken ->
                        loadUserQueue(newToken)
                    }
                } else {
                    println("load user queue not error 401")
                    e.printStackTrace()
                    nextInQueue = null
                }
            }
        }

    }



    /**
     * Handles the `!add` chat command. Every outcome is replied to in chat so the viewer knows
     * whether their request worked.
     */
    fun searchAndAddSongToQueue(query: String, requestedBy: String? = null) {
        if (query.isBlank()) {
            reply(requestedBy, "usage: !add <song name>")
            return
        }
        viewModelScope.launch(Dispatchers.Main) {
            val authHeader = "Bearer $spotifyToken"
            try {
                val response: Response<SearchResult> = spotifyApi.searchSong(authHeader, query)
                if (!response.isSuccessful) {
                    println("search failed: ${response.code()} ${response.message()}")
                    reply(requestedBy, "couldn't search Spotify right now, try again later")
                    return@launch
                }
                val track = response.body()?.tracks?.items?.firstOrNull()
                if (track == null) {
                    reply(requestedBy, "couldn't find a song matching \"$query\"")
                } else {
                    addSongToQueue(track, requestedBy)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                reply(requestedBy, "couldn't search Spotify right now, try again later")
            }
        }
    }

    fun addSongToQueue(track: Track, requestedBy: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            val authHeader = "Bearer $spotifyToken"
            try {
                val response: Response<Unit> = spotifyApi.addSongToQueue(authHeader, track.uri)
                if (response.isSuccessful) {
                    spotifyToken?.let { loadUserQueue(it) }
                    recentlyAddedTrack = track
                    reply(requestedBy, "added to queue: ${track.describe()}")
                } else {
                    println("add to queue failed: ${response.code()} ${response.message()}")
                    reply(requestedBy, addFailureMessage(track, response.code()))
                }
            } catch (e: IOException) {
                e.printStackTrace()
                reply(requestedBy, addFailureMessage(track, statusCode = null))
            }
        }
    }

    private fun addFailureMessage(track: Track, statusCode: Int?): String =
        // Spotify answers 404 (NO_ACTIVE_DEVICE) when no player is open and playing.
        if (statusCode == 404) "couldn't add ${track.describe()}: no active Spotify player"
        else "couldn't add ${track.describe()} right now, try again later"

    private fun Track.describe() = "$name by ${artists.joinToString(", ") { it.name }}"

    private fun reply(requestedBy: String?, message: String) {
        responseMessage = message
        sendChatMessage(if (requestedBy != null) "@$requestedBy $message" else message)
    }

    fun refreshTokenAndRetry(onTokenRefreshed: (String) -> Unit) {
        viewModelScope.launch(ioDispatcher) {

            try {
                val newToken = refreshSpotifyToken()
                spotifyToken = newToken
                onTokenRefreshed(newToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectToTwitchChat() {
        AuthUtil.connectToTwitchChat("titchy_bot", Env.twitchChannel)

    }

    public fun sendMessageToTwitch(){
        AuthUtil.sendMessageToTwitchChat("testing...")
    }


}
