package util

import io.github.cdimascio.dotenv.Dotenv

object Env {
    private val dotenv = Dotenv.load()

    val spotifyClientId: String
        get() = dotenv["SPOTIFY_CLIENT_ID"] ?: error("SPOTIFY_CLIENT_ID not found")

    val spotifyClientSecret: String
        get() = dotenv["SPOTIFY_CLIENT_SECRET"] ?: error("SPOTIFY_CLIENT_SECRET not found")

    val redirectUri: String
        get() = dotenv["REDIRECT_URI"] ?: error("REDIRECT_URI not found")
}
