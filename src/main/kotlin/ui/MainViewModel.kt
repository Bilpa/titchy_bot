package ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import model.*

import network.SpotifyApi
import retrofit2.HttpException
import retrofit2.Response
import util.AuthUtil
import util.Env

class MainViewModel : ViewModel() {
    var authToken by mutableStateOf<String?>(null)
    var userProfile by mutableStateOf<SpotifyUser?>(null)
    var currentlyPlaying by mutableStateOf<CurrentlyPlaying?>(null)
    var nextInQueue by mutableStateOf<Track?>(null)
    var responseMessage by mutableStateOf<String?>(null)

    private var pollingJob: Job? = null

    fun authenticate() {
        AuthUtil.authenticate(Env.spotifyClientId, Env.spotifyClientSecret) { token ->

            authToken = token
            loadUserProfile(token)
            startPollingCurrentlyPlaying(token)


        }
    }

    private fun startPollingCurrentlyPlaying(token: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive){
                loadCurrentlyPlaying(token)
                delay(5000)
            }
        }
    }

    private fun loadUserProfile(token: String) {
        viewModelScope.launch {
            val authHeader = "Bearer $token"
            try {
                val profile = SpotifyApi.apiService.getCurrentUserProfile(authHeader)
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
                val response: Response<CurrentlyPlaying?> = SpotifyApi.apiService.getCurrentlyPlaying(authHeader)

                if (response.isSuccessful){
                    currentlyPlaying = response.body()
                    println("load currently playing...")
                    println("response = ${response.body()}")
                    loadUserQueue(token)
                }
                else {
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

                val response: Response<UserQueue?> = SpotifyApi.apiService.getUserQueue(authHeader)
                println("response = ${response.body()}")
                if (response.isSuccessful){
                    nextInQueue = response.body()?.queue?.firstOrNull()
                } else{
                    nextInQueue = null
                }
            } catch (e:HttpException){
                if (e.code() == 401) {
                    refreshTokenAndRetry { newToken ->
                        loadUserQueue(newToken)
                    }
                } else {
                    e.printStackTrace()
                    nextInQueue = null
                }
            }
        }

    }

    fun searchAndAddSongToQueue(query: String){
        viewModelScope.launch(Dispatchers.Main) {
            val authHeader = "Bearer $authToken"

            try {
                val response: Response<SearchResult> = SpotifyApi.apiService.searchSong(authHeader,query)
                if(response.isSuccessful){
                    val track = response.body()?.tracks?.items?.firstOrNull()
                    if(track != null){
                        addSongToQueue(track.uri)
                    } else{
                        responseMessage = "Song not found"
                    }
                } else{
                    responseMessage = "search failed"
                }
            } catch (e:HttpException){
                if (e.code() == 401){
                    refreshTokenAndRetry { searchAndAddSongToQueue(query) }
                } else{
                    e.printStackTrace()
                    responseMessage = "Failed to search"
                }
            }
        }
    }

    fun addSongToQueue(uri: String){
        viewModelScope.launch(Dispatchers.Main) {
            val authHeader = "Bearer ${authToken}"

            try {
                val response: Response<Unit> = SpotifyApi.apiService.addSongToQueue(authHeader, uri)
                if (response.isSuccessful){
                    loadUserQueue(authToken!!)
                    responseMessage = "Song added to queue"
                } else{
                    println("uri = ${uri}")
                    responseMessage = "Failed to add song to queue"
                }
            } catch (e:HttpException){
                if(e.code() == 401){
                    refreshTokenAndRetry {
                        addSongToQueue(uri)
                    }
                } else{
                    e.printStackTrace()
                    responseMessage = "Failed to add song to queue"
                }
            }
        }
    }

    private fun refreshTokenAndRetry(onTokenRefreshed: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {

            try {
                val newToken = AuthUtil.refreshToken(Env.spotifyClientId, Env.spotifyClientSecret)
                authToken = newToken
                onTokenRefreshed(newToken)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
