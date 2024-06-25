package util

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicReference


object AuthUtil {

    private const val redirectUri = "http://localhost:8080/callback"
    private val authCode = AtomicReference<String?>()
    private lateinit var refreshToken: String

    fun authenticate(
        clientId: String,
        clientSecret: String,

        onTokenReceived: (String) -> Unit
    ) {

        //start local server to listen for the redirect
        embeddedServer(Netty, port = 8080) {
            routing {
                get("/callback") {
                    val code = call.parameters["code"]
                    authCode.set(code)
                    call.respondText("Authentication successful! You can close this window.", ContentType.Text.Html)
                }
            }
        }.start()

        // Open Spotify authentication URL
        val authUri =
            URI("https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&scope=user-read-email%20user-read-private%20user-read-currently-playing%20user-read-playback-state")
        Desktop.getDesktop().browse(authUri)

        // Wait until auth code is set
        while (authCode.get() == null) {
            Thread.sleep(100)
        }

        // Exchange auth code for access token
        val code = authCode.get()!!
        val response = getAccessToken(code, clientId, clientSecret)
        val accessToken =  response["access_token"].asString
        refreshToken = response["refresh_token"].asString

        onTokenReceived(accessToken)
    }


    private fun getAccessToken(code: String, clientId: String, clientSecret: String): JsonObject {
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

    fun refreshToken(clientId: String, clientSecret: String): String {
        val client = HttpClient.newBuilder().build()
        println("refreshing...")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=refresh_token&refresh_token=$refreshToken&client_id=$clientId&client_secret=$clientSecret"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = Gson().fromJson(response.body(), JsonObject::class.java)
        return json["access_token"].asString
    }

}
