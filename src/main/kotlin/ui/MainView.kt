package ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme


import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun MainView(viewModel: MainViewModel) {
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
            }
        }
    }
}
