import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ui.MainView
import ui.MainViewModel
import util.AuthUtil
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

    var borderVisible by remember { mutableStateOf(true) }

    Window(onCloseRequest = ::exitApplication, undecorated = false, transparent = false) {
        val viewModel = remember { MainViewModel() }


        AuthUtil.viewModel = viewModel
        Box ( modifier = Modifier.fillMaxSize().then(if(borderVisible) Modifier.border(2.dp, Color.Red) else Modifier)){
            MainView(viewModel)
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Checkbox(
                checked = borderVisible,
                onCheckedChange = { borderVisible = it }
            )

        }


    }
}
