package ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import model.SpotifyUser
import network.SpotifyApi
import util.AuthUtil
import util.Env

class MainViewModel : ViewModel() {
    var authToken by mutableStateOf<String?>(null)
    var userProfile by mutableStateOf<SpotifyUser?>(null)

    fun authenticate() {
        AuthUtil.authenticate(Env.spotifyClientId, Env.spotifyClientSecret, Env.redirectUri) { token ->
            authToken = token
            loadUserProfile(token)
        }
    }

    private fun loadUserProfile(token: String) {
        viewModelScope.launch {
            val authHeader = "Bearer $token"
            try {
                val profile = SpotifyApi.apiService.getCurrentUserProfile(authHeader)
                userProfile = profile
            } catch (e: Exception) {
                e.printStackTrace()
                userProfile = null
            }
        }
    }
}
