package util

import io.github.cdimascio.dotenv.Dotenv

object Env {
    private val dotenv = Dotenv.load()

    val spotifyClientId: String
        get() = dotenv["SPOTIFY_CLIENT_ID"] ?: error("SPOTIFY_CLIENT_ID not found")

    val spotifyClientSecret: String
        get() = dotenv["SPOTIFY_CLIENT_SECRET"] ?: error("SPOTIFY_CLIENT_SECRET not found")

    val twitchRedirectUri: String
        get() = dotenv["TWITCH_REDIRECT_URI"] ?: error("REDIRECT_URI not found")

    val spotifyRedirectUri: String
        get() = dotenv["SPOTIFY_REDIRECT_URI"] ?: error("REDIRECT_URI not found")

    val twitchClientId: String
        get() = dotenv["TWITCH_CLIENT_ID"] ?: error("TWITCH_CLIENT_ID not found")

    val twitchClientSecret: String
        get( )= dotenv["TWITCH_CLIENT_SECRET"] ?: error("TWITCH_CLIENT_SECRET not found")

    val botOauthToken: String
        get() = dotenv["BOT_OAUTH_TOKEN"] ?: error(("BOT_OAUTH_TOKEN not found"))
}
