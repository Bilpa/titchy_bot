package network

import com.google.gson.GsonBuilder
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the Retrofit interface against a local MockWebServer instead of api.spotify.com.
 * The Retrofit/Gson setup mirrors [SpotifyApi], whose base URL is fixed and can't be pointed here.
 */
class SpotifyApiServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: SpotifyApiService

    private val authHeader = "Bearer test-token"

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(SpotifyApiService::class.java)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun trackJson(name: String, artist: String, uri: String) =
        """{"name":"$name","artists":[{"name":"$artist"}],"uri":"$uri"}"""

    @Test
    fun `getCurrentUserProfile sends auth header and parses profile`() = runTest {
        enqueueJson("""{"display_name":"Streamer","id":"user123","email":"streamer@example.com"}""")

        val profile = service.getCurrentUserProfile(authHeader)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/me", request.path)
        assertEquals(authHeader, request.getHeader("Authorization"))
        assertEquals("Streamer", profile.display_name)
        assertEquals("user123", profile.id)
    }

    @Test
    fun `getCurrentlyPlaying parses the playing track`() = runTest {
        enqueueJson("""{"item":${trackJson("Song A", "Artist A", "spotify:track:a")}}""")

        val response = service.getCurrentlyPlaying(authHeader)

        assertEquals("/v1/me/player/currently-playing", server.takeRequest().path)
        assertTrue(response.isSuccessful)
        val track = assertNotNull(response.body()?.item)
        assertEquals("Song A", track.name)
        assertEquals("Artist A", track.artists.single().name)
        assertEquals("spotify:track:a", track.uri)
    }

    @Test
    fun `getCurrentlyPlaying returns a successful null body when nothing is playing`() = runTest {
        // Spotify answers 204 No Content when playback is idle.
        server.enqueue(MockResponse().setResponseCode(204))

        val response = service.getCurrentlyPlaying(authHeader)

        assertTrue(response.isSuccessful)
        assertNull(response.body())
    }

    @Test
    fun `getCurrentlyPlaying reports 401 as an unsuccessful response rather than throwing`() = runTest {
        // MainViewModel relies on this: a Response<T> return type never throws HttpException.
        enqueueJson("""{"error":{"status":401,"message":"The access token expired"}}""", code = 401)

        val response = service.getCurrentlyPlaying(authHeader)

        assertFalse(response.isSuccessful)
        assertEquals(401, response.code())
    }

    @Test
    fun `getUserQueue parses queued tracks in order`() = runTest {
        enqueueJson(
            """{"currently_playing":null,"queue":[${trackJson("Next", "Artist B", "spotify:track:b")},${trackJson("Later", "Artist C", "spotify:track:c")}]}"""
        )

        val response = service.getUserQueue(authHeader)

        assertEquals("/v1/me/player/queue", server.takeRequest().path)
        val queue = assertNotNull(response.body()).queue
        assertEquals(listOf("Next", "Later"), queue.map { it.name })
    }

    @Test
    fun `addSongToQueue posts the track uri as a query parameter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = service.addSongToQueue(authHeader, "spotify:track:abc")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/me/player/queue", request.requestUrl?.encodedPath)
        assertEquals("spotify:track:abc", request.requestUrl?.queryParameter("uri"))
        assertEquals(authHeader, request.getHeader("Authorization"))
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `searchSong uses track type and limit 1 by default`() = runTest {
        enqueueJson("""{"tracks":{"items":[${trackJson("Found", "Artist D", "spotify:track:d")}]}}""")

        val response = service.searchSong(authHeader, "never gonna give you up")

        val url = assertNotNull(server.takeRequest().requestUrl)
        assertEquals("/v1/search", url.encodedPath)
        assertEquals("never gonna give you up", url.queryParameter("q"))
        assertEquals("track", url.queryParameter("type"))
        assertEquals("1", url.queryParameter("limit"))
        assertEquals("Found", response.body()?.tracks?.items?.firstOrNull()?.name)
    }

    @Test
    fun `searchSong with no matches yields an empty item list`() = runTest {
        enqueueJson("""{"tracks":{"items":[]}}""")

        val response = service.searchSong(authHeader, "zzzz no such song")

        assertNull(response.body()?.tracks?.items?.firstOrNull())
    }
}
