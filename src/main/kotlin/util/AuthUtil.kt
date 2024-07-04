package util

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.pircbotx.PircBotX
import org.pircbotx.Configuration

import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.ConnectEvent
import org.pircbotx.hooks.events.MessageEvent
import ui.MainViewModel
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference





object AuthUtil {

    private const val redirectUri = "http://localhost:8080/callback"
    private val spotifyAuthCode = AtomicReference<String?>()
    private val twitchAuthCode = AtomicReference<String?>()
    private lateinit var spotifyRefreshToken: String
    private lateinit var twitchRefreshToken: String

    private lateinit var twitchBot: PircBotX

    lateinit var viewModel: MainViewModel
    init {
        startServer()
    }



    private fun startServer() {
        //start local server to listen for the redirect
        embeddedServer(Netty, port = 8080) {
            routing {
                get("/callback") {
                    val code = call.parameters["code"]
                    val state = call.parameters["state"]
                    if (state == "spotify"){
                        spotifyAuthCode.set(code)
                    }
                    else if(state == "twitch"){
                        twitchAuthCode.set(code)
                    }

                    call.respondText("Authentication successful! You can close this window.", ContentType.Text.Html)
                }
            }
        }.start(wait = false)
    }

    fun authenticate(
        clientId: String,
        clientSecret: String,
        target: String,
        onTokenReceived: (String) -> Unit
    ) {
        println("target = ${target}")
        if (target == "spotify") {
            // Open Spotify authentication URL
            val authUri = URI("https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&scope=user-read-email%20user-read-private%20user-read-currently-playing%20user-read-playback-state%20user-modify-playback-state&state=spotify")
            Desktop.getDesktop().browse(authUri)

            waitForAuthCode(target, clientId, clientSecret, onTokenReceived)

        }
        else if (target == "twitch") {
            // Open twitch authentication URL
            val authUri = URI("https://id.twitch.tv/oauth2/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&scope=user:read:email&state=twitch")
            Desktop.getDesktop().browse(authUri)

            waitForAuthCode(target, clientId, clientSecret, onTokenReceived)
        }
    }

    private fun waitForAuthCode(
        target: String,
        clientId: String,
        clientSecret: String,
        onTokenReceived: (String) -> Unit
    ) {
        val authCode = if (target == "spotify") spotifyAuthCode else twitchAuthCode

        while (authCode.get() == null) {
            Thread.sleep(100)
        }

        val code = authCode.get()!!
        val response = if (target == "spotify") getSpotifyAccessToken(code, clientId, clientSecret) else getTwitchAccessToken(code, clientId, clientSecret)
        val accessToken = response["access_token"].asString

        if (target == "spotify") {
            spotifyRefreshToken = response["refresh_token"].asString
        } else {
            twitchRefreshToken = response["refresh_token"].asString
        }

        onTokenReceived(accessToken)
    }

    private fun getTwitchAccessToken(code: String, clientId: String, clientSecret: String): JsonObject {
        val client = HttpClient.newBuilder().build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://id.twitch.tv/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&code=$code&redirect_uri=$redirectUri&client_id=$clientId&client_secret=$clientSecret"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = Gson().fromJson(response.body(), JsonObject::class.java)
        println("json = ${json}")
        return json
    }




    private fun getSpotifyAccessToken(code: String, clientId: String, clientSecret: String): JsonObject {
        val client = HttpClient.newBuilder().build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&code=$code&redirect_uri=${redirectUri}&client_id=$clientId&client_secret=$clientSecret"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = Gson().fromJson(response.body(), JsonObject::class.java)
        println("json = ${json}")
        return json

    }

    fun refreshSpotifyToken(clientId: String, clientSecret: String): String {
        val client = HttpClient.newBuilder().build()
        println("refreshing...")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=$spotifyRefreshToken&client_id=$clientId&client_secret=$clientSecret"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = Gson().fromJson(response.body(), JsonObject::class.java)
        return json["access_token"].asString
    }

    fun connectToTwitchChat(nickname: String, oauthToken: String, channel: String) {
        val config = Configuration.Builder()
            .setName(nickname)
            .setServerHostname("irc.chat.twitch.tv")
            .setServerPort(6667)
            .setServerPassword(Env.botOauthToken)
            .addAutoJoinChannel("#$channel")
            .addListener(object : ListenerAdapter() {
                override fun onMessage(event: MessageEvent) {
                    println("[Twitch Chat] ${event.user?.nick}: ${event.message}")
                    if (event.message.startsWith("!add")){
                        val song = event.message.substringAfter(" ")
                        sendMessageToTwitchChat("${event.user?.nick} wants to add ${song}")
                        viewModel.searchAndAddSongToQueue(song)
                    }
                }
            })
            .buildConfiguration()

        twitchBot = PircBotX(config)
        val botThread = Thread {
            twitchBot.startBot()
        }
        botThread.isDaemon = true
        botThread.start()
    }

    fun sendMessageToTwitchChat(message: String) {
        twitchBot.sendIRC().message("#muminslayer", message)
    }

    fun disconnectFromTwitchChat() {
        twitchBot.stopBotReconnect()
        twitchBot.close()
    }


    // Function to listen for messages in Twitch chat


}
