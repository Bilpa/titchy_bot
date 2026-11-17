package ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import model.Artist
import model.CurrentlyPlaying
import model.SearchResult
import model.SpotifyUser
import model.Track
import model.Tracks
import model.UserQueue
import network.SpotifyApiService
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [MainViewModel] with a fake Spotify API and a virtual-time test dispatcher standing in for
 * both Dispatchers.Main and Dispatchers.IO.
 *
 * The scheduler is advanced manually rather than through runTest: once polling starts it loops
 * forever, and runTest's end-of-test drain would never finish.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val api = FakeSpotifyApiService()
    private var refreshCount = 0
    private lateinit var viewModel: MainViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = MainViewModel(
            spotifyApi = api,
            refreshSpotifyToken = { refreshCount++; FRESH_TOKEN },
            ioDispatcher = dispatcher,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Spotify authentication and polling ---

    @Test
    fun `after authentication the profile, current track and queue are loaded`() {
        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()

        assertEquals("Streamer", viewModel.userProfile?.display_name)
        assertEquals("Now Playing", viewModel.currentlyPlaying?.item?.name)
        assertEquals("Next", viewModel.nextInQueue?.name)
        assertEquals("current queue is: 1. Next by Artist B 2. Later by Artist C ", viewModel.currentQueue)
    }

    @Test
    fun `currently playing is polled every five seconds`() {
        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()
        assertEquals(1, api.callCount("currently-playing"))

        scheduler.advanceTimeBy(4_999)
        scheduler.runCurrent()
        assertEquals(1, api.callCount("currently-playing"))

        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        assertEquals(2, api.callCount("currently-playing"))
    }

    @Test
    fun `nothing playing leaves currentlyPlaying null but still loads the queue`() {
        api.currentlyPlaying = { Response.success<CurrentlyPlaying?>(204, null) }

        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()

        assertNull(viewModel.currentlyPlaying)
        assertEquals("Next", viewModel.nextInQueue?.name)
    }

    // --- Token refresh and retry ---

    @Test
    fun `401 on currently playing refreshes the token and retries with the new one`() {
        api.acceptOnly(FRESH_TOKEN)

        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()

        assertEquals(1, refreshCount)
        assertEquals(FRESH_TOKEN, viewModel.spotifyToken)
        assertEquals("Now Playing", viewModel.currentlyPlaying?.item?.name)
        assertEquals("Bearer $FRESH_TOKEN", api.lastAuthHeader("currently-playing"))
    }

    @Test
    fun `polling after a refresh uses the refreshed token`() {
        api.acceptOnly(FRESH_TOKEN)
        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()
        val refreshesAfterStartup = refreshCount

        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()

        assertEquals(refreshesAfterStartup, refreshCount, "no further refresh once the token is valid")
        assertEquals("Bearer $FRESH_TOKEN", api.lastAuthHeader("currently-playing"))
    }

    @Test
    fun `any unsuccessful currently-playing response triggers a token refresh, not only 401`() {
        // Documents existing behaviour: loadCurrentlyPlaying refreshes on every non-2xx response.
        var served = 0
        api.currentlyPlaying = { if (served++ == 0) errorResponse(500) else Response.success(CurrentlyPlaying(NOW_PLAYING)) }

        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()

        assertEquals(1, refreshCount)
        assertEquals("Now Playing", viewModel.currentlyPlaying?.item?.name)
    }

    @Test
    fun `401 on profile refreshes the token and retries`() {
        var calls = 0
        api.profile = { if (calls++ == 0) throw HttpException(errorResponse<SpotifyUser>(401)) else STREAMER }

        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()

        assertTrue(refreshCount >= 1)
        assertEquals("Bearer $FRESH_TOKEN", api.lastAuthHeader("me"))
        assertEquals("Streamer", viewModel.userProfile?.display_name)
    }

    // --- Queue ---

    @Test
    fun `failed queue load reports failure and clears next in queue`() {
        api.queue = { errorResponse(500) }

        viewModel.onSpotifyAuthenticated(OLD_TOKEN)
        scheduler.runCurrent()

        assertEquals("failed to load queue", viewModel.currentQueue)
        assertNull(viewModel.nextInQueue)
    }

    // --- Search and add (the !add chat command) ---

    @Test
    fun `searchAndAddSongToQueue queues the first search hit and reloads the queue`() {
        viewModel.spotifyToken = OLD_TOKEN

        viewModel.searchAndAddSongToQueue("some song")
        scheduler.advanceUntilIdle()

        assertEquals("some song", api.lastSearchQuery)
        assertEquals(listOf(FOUND.uri), api.addedUris)
        assertEquals(FOUND, viewModel.recentlyAddedTrack)
        assertEquals("Song added to queue", viewModel.responseMessage)
        assertEquals(1, api.callCount("queue"))
    }

    @Test
    fun `searchAndAddSongToQueue reports when no song matches`() {
        viewModel.spotifyToken = OLD_TOKEN
        api.search = { Response.success(SearchResult(Tracks(emptyList()))) }

        viewModel.searchAndAddSongToQueue("zzzz")
        scheduler.advanceUntilIdle()

        assertEquals("Song not found", viewModel.responseMessage)
        assertTrue(api.addedUris.isEmpty())
    }

    @Test
    fun `searchAndAddSongToQueue reports a failed search`() {
        viewModel.spotifyToken = OLD_TOKEN
        api.search = { errorResponse(500) }

        viewModel.searchAndAddSongToQueue("some song")
        scheduler.advanceUntilIdle()

        assertEquals("search failed", viewModel.responseMessage)
        assertTrue(api.addedUris.isEmpty())
    }

    @Test
    fun `addSongToQueue reports failure and does not mark the track as added`() {
        viewModel.spotifyToken = OLD_TOKEN
        api.addToQueue = { errorResponse(403) }

        viewModel.addSongToQueue(FOUND)
        scheduler.advanceUntilIdle()

        assertEquals("Failed to add song to queue", viewModel.responseMessage)
        assertNull(viewModel.recentlyAddedTrack)
    }

    private companion object {
        const val OLD_TOKEN = "old-token"
        const val FRESH_TOKEN = "fresh-token"

        val STREAMER = SpotifyUser(display_name = "Streamer", id = "user123", email = "streamer@example.com")
        val NOW_PLAYING = track("Now Playing", "Artist A")
        val FOUND = track("Found", "Artist D")
        val QUEUE = UserQueue(currently_playing = null, queue = listOf(track("Next", "Artist B"), track("Later", "Artist C")))

        fun track(name: String, artist: String) =
            Track(name = name, artists = listOf(Artist(artist)), uri = "spotify:track:${name.lowercase().replace(' ', '-')}")

        fun <T> errorResponse(code: Int): Response<T> = Response.error(code, "".toResponseBody())
    }

    /** Records every call; each endpoint's behaviour is a replaceable lambda defaulting to success. */
    private class FakeSpotifyApiService : SpotifyApiService {
        private val calls = mutableListOf<Pair<String, String>>()
        val addedUris = mutableListOf<String>()
        var lastSearchQuery: String? = null

        var profile: (String) -> SpotifyUser = { STREAMER }
        var currentlyPlaying: (String) -> Response<CurrentlyPlaying?> = { Response.success(CurrentlyPlaying(NOW_PLAYING)) }
        var queue: (String) -> Response<UserQueue?> = { Response.success(QUEUE) }
        var addToQueue: (String) -> Response<Unit> = { Response.success(Unit) }
        var search: (String) -> Response<SearchResult> = { Response.success(SearchResult(Tracks(listOf(FOUND)))) }

        fun callCount(endpoint: String) = calls.count { it.first == endpoint }
        fun lastAuthHeader(endpoint: String) = calls.last { it.first == endpoint }.second

        /** Makes the Response-returning player endpoints answer 401 to any token except [token]. */
        fun acceptOnly(token: String) {
            currentlyPlaying = { header ->
                if (header == "Bearer $token") Response.success(CurrentlyPlaying(NOW_PLAYING)) else errorResponse(401)
            }
            queue = { header -> if (header == "Bearer $token") Response.success(QUEUE) else errorResponse(401) }
        }

        override suspend fun getCurrentUserProfile(authHeader: String): SpotifyUser {
            calls += "me" to authHeader
            return profile(authHeader)
        }

        override suspend fun getCurrentlyPlaying(authHeader: String): Response<CurrentlyPlaying?> {
            calls += "currently-playing" to authHeader
            return currentlyPlaying(authHeader)
        }

        override suspend fun getUserQueue(authHeader: String): Response<UserQueue?> {
            calls += "queue" to authHeader
            return queue(authHeader)
        }

        override suspend fun addSongToQueue(authHeader: String, uri: String): Response<Unit> {
            calls += "add" to authHeader
            addedUris += uri
            return addToQueue(authHeader)
        }

        override suspend fun searchSong(authHeader: String, query: String, type: String, limit: Int): Response<SearchResult> {
            calls += "search" to authHeader
            lastSearchQuery = query
            return search(authHeader)
        }
    }
}
