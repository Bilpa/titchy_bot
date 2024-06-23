package util

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.swing.JOptionPane

object AuthUtil {
    fun authenticate(clientId: String, clientSecret: String, redirectUri: String, onTokenReceived: (String) -> Unit) {
        val authUri = URI("https://accounts.spotify.com/authorize?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&scope=user-read-email%20user-read-private")
        Desktop.getDesktop().browse(authUri)

        val code = JOptionPane.showInputDialog("Enter the code from the redirect URL:")

        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&code=$code&redirect_uri=$redirectUri&client_id=$clientId&client_secret=$clientSecret"))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val json = Gson().fromJson(response.body(), JsonObject::class.java)
        val accessToken = json["access_token"].asString

        onTokenReceived(accessToken)
    }
}
