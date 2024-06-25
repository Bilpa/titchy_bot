package ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme


import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun MainView(viewModel: MainViewModel) {

    var trackUri by remember { mutableStateOf("") }

    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            if (viewModel.authToken == null) {
                Button(onClick = {
                    viewModel.authenticate()
                }) {
                    Text("Authenticate with Spotify")
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
                    value = trackUri,
                    onValueChange = {trackUri = it},
                    label = { Text("Track Uri") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    viewModel.addSongToQueue(trackUri)
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
