package com.beiwords.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.beiwords.app.ui.BeiWordsTheme
import com.beiwords.app.ui.BooksScreen
import com.beiwords.app.ui.HomeScreen
import com.beiwords.app.ui.ImportScreen
import com.beiwords.app.ui.StudyScreen

sealed class Screen {
    object Home : Screen()
    object Study : Screen()
    object Books : Screen()
    object Import : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeiWordsTheme {
                AppNav()
            }
        }
    }
}

@Composable
fun AppNav() {
    val screen = rememberSaveable { mutableStateOf("Home") }
    Surface(modifier = Modifier.fillMaxSize()) {
        when (screen.value) {
            "Home" -> HomeScreen(
                onStudy = { screen.value = "Study" },
                onBooks = { screen.value = "Books" },
                onImport = { screen.value = "Import" }
            )
            "Study" -> StudyScreen(onExit = { screen.value = "Home" })
            "Books" -> BooksScreen(
                onBack = { screen.value = "Home" },
                onImport = { screen.value = "Import" }
            )
            "Import" -> ImportScreen(onBack = { screen.value = "Books" })
        }
    }
}
