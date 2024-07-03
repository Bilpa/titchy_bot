package ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*


import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun MainView(viewModel: MainViewModel) {

    var trackTitle by remember { mutableStateOf("") }

    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.recentlyAddedTrack){
        viewModel.recentlyAddedTrack?.let { track ->
            val result = snackBarHostState.showSnackbar(
                message = "Added to Queue: ${track.name} by ${track.artists.joinToString(", ") { it.name }}",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed){
                viewModel.recentlyAddedTrack = null
            }

        }
    }

    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {

            SnackbarHost(hostState = snackBarHostState)

            if (viewModel.spotifyToken == null || viewModel.twitchToken == null) {
                Button(onClick = {
                    viewModel.authenticateWithSpotify()
                }) {
                    Text("Authenticate with Spotify")
                }
                Button(onClick = {
                    viewModel.authenticateWithTwitch()
                }) {
                    Text("Authenticate with Twitch")
                }
            } else {
                viewModel.userProfile?.let {
                    Text("Welcome, ${it.display_name}!")
                    Text("ID: ${it.id}")
                    Text("Email: ${it.email}")
                } ?: Text("Loading profile...")

                viewModel.currentlyPlaying?.item?.let {

                    println(it.uri)
                    Text("Currently Playing: ${it.name} by ${it.artists.joinToString(", ") { artist -> artist.name }}")
                } ?: Text("Not currently playing anything")

                viewModel.nextInQueue?.let {
                    Text("Next in queue: ${it.name} by ${it.artists.joinToString(", ") {artist -> artist.name}}")
                } ?: Text("Nothing in queue")


                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = trackTitle,
                    onValueChange = {trackTitle = it},
                    label = { Text("song title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    viewModel.searchAndAddSongToQueue(trackTitle)
                }){
                    Text("Add to Queue")
                }

                viewModel.responseMessage?.let {
                    Text(it)
                }
            }
        }
    }
}


