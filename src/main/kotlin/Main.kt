import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ui.MainView
import ui.MainViewModel
import util.Env

@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
        Button(onClick = {
            text = "Hello, ${Env.spotifyClientId}!"
        }) {
            Text(text)
        }
    }
}

fun main() = application {
    println("starting app...")
    Window(onCloseRequest = ::exitApplication) {
        val viewModel = remember { MainViewModel() }
        MainView(viewModel)
    }
}
